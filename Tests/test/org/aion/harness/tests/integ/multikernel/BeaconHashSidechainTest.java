package org.aion.harness.tests.integ.multikernel;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.aion.equihash.EquihashMiner;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.PrivateKey;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.main.NodeFactory.NodeType;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.Event;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.types.Block;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.util.SimpleLog;
import org.aion.util.bytes.ByteUtil;
import org.apache.commons.codec.binary.Hex;
import org.junit.AfterClass;
import org.junit.Test;

/**
 * Test that a kernel can import blocks containing beacon hashes on to a side chain.
 *
 * SETUP INFORMATION -- this test needs two kernels.  They are assumed to be in the following
 * locations:
 *   1) TEST_HARNESS_ROOT/Tests/aion
 *   2) TEST_HARNESS_ROOT/Tests/aion2
 *
 * Visualization of this case (x is the block of beacon hash , * is the new block being attached):
 *
 * <tt>
 *   o---o--o--o--o   main chain
 *   |
 *    \--x--o--*      side chain
 * </tt>
 *
 * @implNote This test must not be used in ConcurrentSuite because it needs to have
 * control over what blocks are present in the database.  It is also not suitable to
 * be run via ConcurrentRunner/SequentialRunner because it needs to run two kernels
 * and reconfigure them.
 */
public class BeaconHashSidechainTest {
    private static BeaconHashSidechainNodeManager manager1;
    private static BeaconHashSidechainNodeManager manager2;

    private final EquihashMiner miner = new EquihashMiner("127.0.0.1", "8102");

    private static final RPC rpc1 = RPC.newRpc("127.0.0.1", "8101");
    private static final RPC rpc2 = RPC.newRpc("127.0.0.1", "8102");

    private final String PREMINED_KEY = "4c3c8a7c0292bc55d97c50b4bdabfd47547757d9e5c194e89f66f25855baacd0";
    private final String PREMINED_ADDRESS = "0xa027e3441b6283222e3ce56d4c08b95f9cc2146dfe43ca697833ebdf413cd24a";

    private static final SimpleLog log = new SimpleLog(BeaconHashSidechainTest.class.getName());

    public BeaconHashSidechainTest() {
        manager1 = new BeaconHashSidechainNodeManager(NodeType.JAVA_NODE,
            System.getProperty("user.dir") + "/aion",
            System.getProperty("user.dir") + "/test_resources/aioncustom1"
        );

        manager2 = new BeaconHashSidechainNodeManager(NodeType.JAVA_NODE,
            System.getProperty("user.dir") + "/aion2",
            System.getProperty("user.dir") + "/test_resources/aioncustom2"
        );
    }

    @AfterClass
    public static void afterClass() {
        // will catch all exceptions and print them, so that an
        // exception while shutting down manager1 doesn't cause
        // us to skip shutting down manager2.  doesn't really matter
        // that exceptions won't be raised -- the test is exiting anyway

        try {
            if (manager1 != null && manager1.isKernelRunning()) {
                manager1.shutdownLocalNode();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            if (manager2 != null && manager2.isKernelRunning()) {
                manager2.shutdownLocalNode();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test() throws Exception {
        NodeListener listener1;
        NodeListener listener2;

        // Start up kernel2, create a transaction in block num 2
        // that uses block num 1 as beacon hash, then let it mine until its
        // chain reaches 8 blocks.  This sets up the chain to test the
        // sidechain sync logic.

        miner.startMining();
        manager2.startLocalNode(true);
        listener2 = manager2.newNodeListener();
        log.log("Started kernel #2");

        Map<Long, String> minedBlocks = awaitBlockSealedAndCapture(listener2, 1);

        Block firstBlock = getRpcResultOrThrow(rpc2.getBlockByNumber(BigInteger.ONE),
            "Can't get block 1 from kernel2");

        Address addr = new Address(ByteUtil.hexStringToBytes(PREMINED_ADDRESS));
        PrivateKey preminedAccount = PrivateKey.fromBytes(Hex.decodeHex(PREMINED_KEY));
        SignedTransaction transaction = SignedTransaction.newGeneralTransaction(
            preminedAccount,
            BigInteger.ZERO,
            addr,
            null,
            2_000_000,
            10_000_000_000L,
            BigInteger.ONE,
            firstBlock.hash);
        ReceiptHash th = getRpcResultOrThrow(rpc2.sendSignedTransaction(transaction),
            "Coulnd't get hash of transaction after sending transaction to kernel2");

        minedBlocks.putAll(awaitBlockSealedAndCapture(listener2, 8));

        TransactionReceipt tr = getRpcResultOrThrow(rpc2.getTransactionReceipt(th),
            "Couldn't get transaction receipt after sending transaction to kernel2");

        String txHash = ByteUtil.toHexString(tr.getTransactionHash());
        long txBn = tr.getBlockNumber().longValue();
        String txBlockHash = ByteUtil.toHexString(tr.getBlockHash());

        log.log(String.format(
            "Transaction sent to node2 with (txHash, txBlockNum, blockHash) = (%s, %d, %s)",
            txHash, txBn, txBlockHash));

        // Turn off kernel2 and start kernel1 (so they don't peer and sync).  Let kernel1
        // mine 3 blocks.  This sets up the condition such that when kernel1 is peered with
        // kernel2, kernel1 syncs the first few blocks of kernel2 as a side-chain (because
        // kernel1 has 3 blocks, the first few blocks from kernel2 form a lower total difficulty)

        log.log("Shutting down kernel #2");
        manager2.shutdownLocalNode();
        log.log("Started kernel #1");
        manager1.startLocalNode(true);
        listener1 = manager1.newNodeListener();

        awaitBlockSealedAndCapture(listener1, 3);

        log.log("Shutting down kernel #1");
        manager1.shutdownLocalNode();
        miner.stopMining();

        // Now turn on both kernels with mining off.  kernel1 will sync blocks from kernel2 since
        // kernel2's total difficulty is larger than that of kernel1.  kernel1 will initially consider
        // those incoming blocks to be a side chain, until it has retrieved enough for that
        // side chain to exceed the total difficulty of its main chain.  Since the transaction
        // using beacon hash is in block num 2, evaluating the validity of that tx's beacon hash
        // will happen when that tx is in a side chain block (the incoming block won't be
        // considered main chain because at that time of the sync, the total difficulty hasn't
        // yet reached that of kernel #1's main chain, which has 3 blocks)

        log.log("Starting kernel #1 and #2 with mining turned off");

        // impl note: you can only listen to the most-recently started kernel because
        // LogManager moves all files from log/ to log/archive/ when it starts up.  luckily,
        // we only need to listen to kernel1.
        manager2.startLocalNode(false);
        manager1.startLocalNode(false);
        listener1 = manager1.newNodeListener();

        // impl note: possible race condition -- this only works because we
        // rely on the behaviour that it takes a few seconds for kernels to peer
        // with one another.  if this happened really fast, the import status
        // messages could appear before we start listening.  luckily, this doesn't
        // seem to ever happen
        List<BlockImportMessage> bims = awaitAndCaptureImportStatus(listener1, minedBlocks);

        // check the block with the transaction
        List<BlockImportMessage> txBim = bims.stream()
            .filter(bim -> bim.number == txBn)
            .collect(Collectors.toList());
        assertThat("unexpected number of block import messages for block number " + txBn,
            txBim.size(), is(1));
        assertThat("unexpected block hash for block number " + txBn,
            txBlockHash.contains(txBim.get(0).shortHash), is(true));
        String importedTxBlockResult = txBim.get(0).result;
        assertThat("block with the transaction should have been successfully imported as a side chain block",
            importedTxBlockResult, is("IMPORTED_NOT_BEST"));

        // check the last block that we know kernel #2 mined was imported successfully
        List<BlockImportMessage> lastBim = bims.stream()
            .filter(bim -> bim.number == 8)
            .collect(Collectors.toList());
        assertThat("unexpected number of block import messages for block number " + txBn,
            txBim.size(), is(1));
        assertThat("unexpected block hash for block number " + txBn,
            txBlockHash.contains(txBim.get(0).shortHash), is(true));
        importedTxBlockResult = lastBim.get(0).result;
        assertThat("block 8 should have been imported successfully as a main chain block",
            importedTxBlockResult, is("IMPORTED_BEST"));

        // All done!
        manager1.shutdownLocalNode();
        manager2.shutdownLocalNode();
    }

    private <T> T getRpcResultOrThrow(RpcResult<T> result, String msg) {
        if(! result.isSuccess()) {
            throw new RuntimeException(String.format("%s (error: %s)",
                msg, result.getError()));
        } else {
            return result.getResult();
        }
    }

    private static final Pattern IMPORT_STATUS_CAPTURE = Pattern.compile(
        "<import-status: node = 222222, hash = (\\w+), number = (\\d+), txs = \\w+, result = (\\w+),");

    /**
     * Blocking-wait on "import status" kernel log messages until all entries in the
     * given Map of (block num, short hash) have been observed.  The block number,
     * hash, and import status are captured
     *
     * @param minedBlocks a map with (blockNumber, shortHash) entries
     * @return captured information from the "import status" message
     * @throws Exception
     *
     */
    private List<BlockImportMessage>
    awaitAndCaptureImportStatus(NodeListener listener, Map<Long, String> minedBlocks)
    throws Exception {
        List<FutureResult<LogEventResult>> expectedFutures = new LinkedList<>();
        for(Long bn : minedBlocks.keySet()) {
            IEvent blockImportedEv = new Event("<import-status: node = 222222, hash = " + minedBlocks.get(bn) + ",");
            FutureResult<LogEventResult> resultFuture =
                listener.listenForEvent(blockImportedEv, 3, TimeUnit.MINUTES);
            expectedFutures.add(resultFuture);
        }

        List<BlockImportMessage> capturedMsgs = new LinkedList<>();
        for(FutureResult<LogEventResult> resultFuture : expectedFutures) {
            LogEventResult res = resultFuture.get(3, TimeUnit.MINUTES);
            if(! res.eventWasObserved()) {
                throw new RuntimeException("Didn't observe import-status string.  Result: " + res.toString());
            }

            String line = res.getObservedLogs().get(0);
            Matcher m = IMPORT_STATUS_CAPTURE.matcher(line);
            m.find();
            BlockImportMessage bim = new BlockImportMessage(m.group(1), Long.parseLong(m.group(2)), m.group(3));
            log.log("Saw import-status message: " + bim.toString());
            capturedMsgs.add(bim);
        }

        return capturedMsgs;
    }

    private static final Pattern BLOCK_SEALED_NUM_CAPTURE = Pattern.compile(
        "block sealed <num=(\\d+), hash=(\\w+)");

    /**
     * Blocking-wait on "block sealed" kernel log messages until given block number
     * has been sealed.  The shortened-hash is captured from the log message and
     * returned in a map.
     *
     * @param listener listener of the log file
     * @param number number to wait until
     * @return map from block number to short hash for all observed "block sealed" messages
     *         observed during the wait
     * @throws Exception
     */
    private Map<Long, String> awaitBlockSealedAndCapture(NodeListener listener,
                                                         long number)
    throws Exception {
        log.log("Waiting for 'block sealed' messages to reach block #" + number);
        Map<Long, String> minedBlocks = new HashMap<>();
        long mostRecentBlockNumberSeen = -1;
        while(mostRecentBlockNumberSeen < number) {
            IEvent blockSealedEv = new Event("block sealed <num=");
            FutureResult<LogEventResult> resultFuture =
                listener.listenForEvent(blockSealedEv, 1, TimeUnit.MINUTES);

            LogEventResult res = resultFuture.get(2, TimeUnit.MINUTES);
            if(! res.eventWasObserved()) {
                throw new RuntimeException("Didn't observe block sealed string");
            }

            String line = res.getObservedLogs().get(0);
            Matcher m = BLOCK_SEALED_NUM_CAPTURE.matcher(line);
            m.find();
            mostRecentBlockNumberSeen = Long.parseLong(m.group(1));

            minedBlocks.put(mostRecentBlockNumberSeen, m.group(2));
            log.log(String.format(
                "Saw block sealed message for (num, hash): (%d, %s)",
                mostRecentBlockNumberSeen, m.group(2)));
        }
        return minedBlocks;
    }

    /** Simple data holder for things about block import message we care about */
    private final static class BlockImportMessage {
        public final String shortHash;
        public final long number;
        public final String result;

        public BlockImportMessage(String shortHash, long number, String result) {
            this.shortHash = shortHash;
            this.number = number;
            this.result = result;
        }

        @Override
        public String toString() {
            return String.format("<hash=%s, num=%d, status=%s>", shortHash, number, result);
        }
    }
}
