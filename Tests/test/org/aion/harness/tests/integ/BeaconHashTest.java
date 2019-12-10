package org.aion.harness.tests.integ;

import static org.aion.harness.tests.contracts.Assertions.assertRpcSuccess;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.kernel.Transaction;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.types.Block;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.tests.contracts.avm.ByteArrayHolder;
import org.aion.harness.tests.integ.runner.SequentialRunner;
import org.aion.harness.tests.integ.runner.internal.LocalNodeListener;
import org.aion.harness.tests.integ.runner.internal.PreminedAccount;
import org.aion.harness.tests.integ.runner.internal.PrepackagedLogEventsFactory;
import org.aion.harness.util.SimpleLog;
import org.aion.util.bytes.ByteUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test the beacon hash feature (AIP010) */
@RunWith(SequentialRunner.class)
public class BeaconHashTest {
    private static final long ENERGY_LIMIT = 1_234_567L;
    private static final long ENERGY_PRICE = 10_010_020_345L;
    private static final long BLOCK_WAIT_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(2);

    private final SimpleLog log = new SimpleLog(BeaconHashTest.class.getName());

    private static RPC rpc = RPC.newDefaultRpc();

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();

    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();

    @Test
    public void testBeaconHash() throws Exception {
        // ensure best block number is larger than 3, then use the block whose
        // number is 2 behind the best one as the block hash to test with.
        long bn = -1;
        long t0 = System.nanoTime();

        while(bn < 6 && System.nanoTime() - t0 < BLOCK_WAIT_TIMEOUT_NANOS) {
            RpcResult<Long> maybeBn = rpc.blockNumber();
            if (!maybeBn.isSuccess()) {
                throw new RuntimeException(
                    "Failed to get block number.  Error was: " + maybeBn.getError());
            }
            bn = maybeBn.getResult();
            if(bn < 6) {
                log.log(String.format(
                    "Waiting 15 sec to wait for best block of chain to exceed 3.  (current bn = %d",
                    bn));
                TimeUnit.SECONDS.sleep(15);
            }
        }
        if(bn == -1) {
            throw new RuntimeException(String.format(
                "Timeout expired for waiting for best block number to reach 3 (best block is %d)",
                bn));
        }

        long beaconBn = bn - 2;
        Block beacon;
        RpcResult<Block> maybeBlock = rpc.getBlockByNumber(BigInteger.valueOf(bn - 2));
        if(! maybeBlock.isSuccess()) {
            throw new RuntimeException(String.format(
                "Failed to retrieve block #%d.  Error was: %s", beaconBn, maybeBlock.getError()));
        }
        beacon = maybeBlock.getResult();

        log.log(String.format(
            "Using block #%d (hash = %s) as the good beacon block.",
            beaconBn,
            ByteUtil.toHexString(beacon.hash)
        ));

        byte[] badBeacon = ByteUtil.hexStringToBytes("0xcafecafecafecafecafecafecafecafecafecafecafecafecafecafecafecafe");
        log.log(String.format(
            "Using hash %s as the bad beacon hash.",
            ByteUtil.toHexString(badBeacon)
        ));

        // build contract deployment Tx with the bad beacon hash to check that deploying it fails
        SignedTransaction badDeploy = SignedTransaction.newAvmCreateTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getNonce(),
            new CodeAndArguments(
                JarBuilder.buildJarForMainAndClasses(ByteArrayHolder.class), new byte[0])
                .encodeToBytes(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO, /* amount */
            badBeacon);

        // send the bad contract deployment Tx and check that it fails
        RpcResult<ReceiptHash> badDeployResult = rpc.sendSignedTransaction(badDeploy);
        assertThat("Transaction with beacon hash that is not present in blockchain should fail",
            badDeployResult.isSuccess(), is(false));
        assertThat(badDeployResult.getError(), badDeployResult.getError().contains("Invalid transaction object") || badDeployResult.getError().contains("Invalid transaction beacon hash"), is(true));

        // build contract deployment Tx with the good beacon hash and check that it succeeds
        SignedTransaction goodDeploy = SignedTransaction.newAvmCreateTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getNonce(),
            new CodeAndArguments(
                JarBuilder.buildJarForMainAndClasses(ByteArrayHolder.class), new byte[0])
                .encodeToBytes(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO, /* amount */
            beacon.hash);

        TransactionReceipt goodDeployReceipt = sendTransaction(goodDeploy);
        assertThat("expected address of deployed contract to be present in receipt of deployment tx",
            goodDeployReceipt.getAddressOfDeployedContract().isPresent(), is(true));
        Address contract = goodDeployReceipt.getAddressOfDeployedContract().get();

        // check initial state of deployed contract
        byte[] result = rpc.call(new Transaction(contract, null));
        assertThat("initial state of deployed contract",
            Arrays.equals(result, new byte[] { 0x1, 0x2 }),
            is(true));

        this.preminedAccount.incrementNonce();

        // set "data" field of the contract using a transaction
        // with bad beacon hash -- it should fail
        byte[] newData1 = new byte[] { 0x21, 0x00, 0x01, 0x61 };
        SignedTransaction badTx1 =
            SignedTransaction.newGeneralTransaction(this.preminedAccount.getPrivateKey(), this.preminedAccount.getNonce(), contract,
                newData1, ENERGY_LIMIT, ENERGY_PRICE, BigInteger.ZERO /* amount */, badBeacon);

        RpcResult<ReceiptHash> badTx1Result = rpc.sendSignedTransaction(badTx1);
        assertThat("tx with invalid beacon hash should not succeed",
            badTx1Result.isSuccess(), is(false));
        assertThat(badDeployResult.getError(), badDeployResult.getError().contains("Invalid transaction object") || badDeployResult.getError().contains("Invalid transaction beacon hash"), is(true));

        // do that transaction agian, this time with the good beacon hash
        SignedTransaction goodTx1 =
            SignedTransaction.newGeneralTransaction(this.preminedAccount.getPrivateKey(), this.preminedAccount.getNonce(), contract,
                newData1, ENERGY_LIMIT, ENERGY_PRICE, BigInteger.ZERO /* amount */, beacon.hash);
        TransactionReceipt tx1Receipt = sendTransaction(goodTx1);
        assertThat("tx with valid beacon hash should succeed",
            tx1Receipt.transactionWasSuccessful(), is(true));
        assertThat(tx1Receipt, is(not(nullValue()))); // can get more rigourous with this
    }

    private TransactionReceipt sendTransaction(SignedTransaction transaction)
        throws InterruptedException, TimeoutException {
        // we want to ensure that the transaction gets sealed into a block.
        IEvent transactionIsSealed = prepackagedLogEventsFactory.build().getTransactionSealedEvent(transaction);
        FutureResult<LogEventResult> future = this.listener.listenForEvent(transactionIsSealed, 5, TimeUnit.MINUTES);

        // Send the transaction off.
        log.log("Sending the transaction...");
        RpcResult<ReceiptHash> sendResult = rpc.sendSignedTransaction(transaction);
        assertRpcSuccess(sendResult);

        // Wait on the future to complete and ensure we saw the transaction get sealed.
        log.log("Waiting for the transaction to process...");
        LogEventResult listenResult = future.get(5, TimeUnit.MINUTES);
        assertTrue(listenResult.eventWasObserved());
        log.log("Transaction was sealed into a block.");

        ReceiptHash hash = sendResult.getResult();

        RpcResult<TransactionReceipt> receiptResult = rpc.getTransactionReceipt(hash);
        assertRpcSuccess(receiptResult);
        return receiptResult.getResult();
    }
}
