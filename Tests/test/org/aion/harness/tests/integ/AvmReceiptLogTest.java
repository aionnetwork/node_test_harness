package org.aion.harness.tests.integ;

import static org.aion.harness.tests.contracts.Assertions.assertRpcSuccess;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aion.avm.common.AvmContract;
import org.aion.avm.common.IAvmStreamingEncoder;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.RawTransaction;
import org.aion.harness.main.NodeFactory.NodeType;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.Event;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionLog;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.result.TransactionResult;
import org.aion.harness.tests.integ.runner.ExcludeNodeType;
import org.aion.harness.tests.integ.runner.SequentialRunner;
import org.aion.harness.tests.integ.runner.internal.LocalNodeListener;
import org.aion.harness.tests.integ.runner.internal.PreminedAccount;
import org.aion.harness.tests.integ.runner.internal.PrepackagedLogEventsFactory;
import org.aion.types.AionAddress;
import org.aion.vm.AvmUtility;
import org.aion.vm.AvmVersion;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests Avm contract logs -- particularly the logs that appear in the transaction receipt. Logs
 * can be queried in other ways..
 */
@RunWith(SequentialRunner.class)
@ExcludeNodeType(NodeType.RUST_NODE) // exclude Rust for now due to bugs that prevent tests from passing
public class AvmReceiptLogTest {
    private static final long ENERGY_LIMIT = 1_234_567L;
    private static final long ENERGY_PRICE = 10_010_020_345L;

    private static final byte[] DATA = new byte[]{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
    private static final byte[] TOPIC1 = new byte[]{ 9, 5, 5, 2, 3, 8, 1 };
    private static final byte[] TOPIC2 = new byte[]{ 8, 8, 0, 1, 4, 2, 0, 1, 2, 6, 8, 3, 4, 6, 8, 8, 0, 1, 4, 2, 0, 1, 2, 6, 8, 3, 4, 6, 8, 8, 0, 1, 4, 2, 0, 1, 2, 6, 8, 3, 4, 6, 8, 8, 0, 1, 4, 2, 0, 1, 2, 6, 8, 3, 4, 6, 8, 8, 0, 1, 4, 2, 0, 1, 2, 6, 8, 3, 4, 6 };
    private static final byte[] TOPIC3 = new byte[]{ 0xf };
    private static final byte[] TOPIC4 = new byte[0];

    private static RPC rpc = new RPC("127.0.0.1", "8545");

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();

    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();

    @Test
    public void testContractWritesNoLogs() throws Exception {
        Address contract = deployLogTargetContract(TestHarnessAvmResources.avmUtility());

        TransactionReceipt receipt = callMethodWriteNoLogs(contract);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getLogs().isEmpty());
    }

    @Test
    public void testContractWritesDataOnlyLog() throws Exception {
        Address contract = deployLogTargetContract(TestHarnessAvmResources.avmUtility());

        TransactionReceipt receipt = callMethodWriteDataOnlyLog(contract);
        assertTrue(receipt.transactionWasSuccessful());
        List<TransactionLog> logs = receipt.getLogs();

        assertEquals(1, logs.size());

        TransactionLog log = logs.get(0);
        assertIsDataOnlyLog(contract, log);
    }

    @Test
    public void testContractWritesLogWithOneTopic() throws Exception {
        Address contract = deployLogTargetContract(TestHarnessAvmResources.avmUtility());

        TransactionReceipt receipt = callMethodWriteDataLogWithOneTopic(contract);
        assertTrue(receipt.transactionWasSuccessful());
        List<TransactionLog> logs = receipt.getLogs();

        assertEquals(1, logs.size());

        TransactionLog log = logs.get(0);
        assertIsLogWithOneTopic(contract, log);
    }

    @Test
    public void testContractWritesLogWithTwoTopics() throws Exception {
        Address contract = deployLogTargetContract(TestHarnessAvmResources.avmUtility());

        TransactionReceipt receipt = callMethodWriteDataLogWithTwoTopics(contract);
        assertTrue(receipt.transactionWasSuccessful());
        List<TransactionLog> logs = receipt.getLogs();

        assertEquals(1, logs.size());

        TransactionLog log = logs.get(0);
        assertIsLogWithTwoTopics(contract, log);
    }

    @Test
    public void testContractWritesLogWithThreeTopics() throws Exception {
        Address contract = deployLogTargetContract(TestHarnessAvmResources.avmUtility());

        TransactionReceipt receipt = callMethodWriteDataLogWithThreeTopics(contract);
        assertTrue(receipt.transactionWasSuccessful());
        List<TransactionLog> logs = receipt.getLogs();

        assertEquals(1, logs.size());

        TransactionLog log = logs.get(0);
        assertIsLogWithThreeTopics(contract, log);
    }

    @Test
    public void testContractWritesLogWithFourTopics() throws Exception {
        Address contract = deployLogTargetContract(TestHarnessAvmResources.avmUtility());

        TransactionReceipt receipt = callMethodWriteDataLogWithFourTopics(contract);
        assertTrue(receipt.transactionWasSuccessful());
        List<TransactionLog> logs = receipt.getLogs();

        assertEquals(1, logs.size());

        TransactionLog log = logs.get(0);
        assertIsLogWithFourTopics(contract, log);
    }

    @Test
    public void testContractWritesMultipleLogs() throws Exception {
        Address contract = deployLogTargetContract(TestHarnessAvmResources.avmUtility());

        TransactionReceipt receipt = callMethodWriteAllLogs(contract);
        assertTrue(receipt.transactionWasSuccessful());
        List<TransactionLog> logs = receipt.getLogs();

        assertEquals(5, logs.size());

        boolean[] foundTopics = new boolean[]{ false, false, false, false, false };
        for (TransactionLog log : logs) {
            List<byte[]> topics = log.copyOfTopics();
            if (topics.size() == 0) {
                assertIsDataOnlyLog(contract, log);
                foundTopics[0] = true;
            } else if (topics.size() == 1) {
                assertIsLogWithOneTopic(contract, log);
                foundTopics[1] = true;
            } else if (topics.size() == 2) {
                assertIsLogWithTwoTopics(contract, log);
                foundTopics[2] = true;
            } else if (topics.size() == 3) {
                assertIsLogWithThreeTopics(contract, log);
                foundTopics[3] = true;
            } else if (topics.size() == 4) {
                assertIsLogWithFourTopics(contract, log);
                foundTopics[4] = true;
            } else {
                fail("Expected topic size to be in range [0,4] but was: " + topics.size());
            }
        }

        // Verify all of the 5 topic sizes were witnessed in the 5 logs.
        for (boolean foundTopic : foundTopics) {
            assertTrue(foundTopic);
        }
    }

    @Test
    public void testContractWritesMultipleLogsAndAlsoLogsInternalCall() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();
        Address callerContract = deployLogTargetContract(avmUtility);
        Address calleeContract = deployLogTargetContract(avmUtility);

        // The internal call will invoke the writeAllLogs method.
        byte[] internalCallData = avmUtility.newAvmStreamingEncoder(AvmVersion.VERSION_1).encodeOneString("writeAllLogs").toBytes();
        TransactionReceipt receipt = callMethodWriteLogsFromInternalCallAlso(callerContract, calleeContract, internalCallData);
        assertTrue(receipt.transactionWasSuccessful());

        // We expect the caller and callee both to call writeAllLogs - so each writes 5 logs
        List<TransactionLog> logs = receipt.getLogs();

        assertEquals(10, logs.size());

        boolean[] foundCallerTopics = new boolean[]{ false, false, false, false, false };
        boolean[] foundCalleeTopics = new boolean[]{ false, false, false, false, false };

        for (TransactionLog log : logs) {
            List<byte[]> topics = log.copyOfTopics();
            if (topics.size() == 0) {

                if (log.address.equals(callerContract)) {
                    assertIsDataOnlyLog(callerContract, log);
                    foundCallerTopics[0] = true;
                } else {
                    assertIsDataOnlyLog(calleeContract, log);
                    foundCalleeTopics[0] = true;
                }

            } else if (topics.size() == 1) {

                if (log.address.equals(callerContract)) {
                    assertIsLogWithOneTopic(callerContract, log);
                    foundCallerTopics[1] = true;
                } else {
                    assertIsLogWithOneTopic(calleeContract, log);
                    foundCalleeTopics[1] = true;
                }

            } else if (topics.size() == 2) {

                if (log.address.equals(callerContract)) {
                    assertIsLogWithTwoTopics(callerContract, log);
                    foundCallerTopics[2] = true;
                } else {
                    assertIsLogWithTwoTopics(calleeContract, log);
                    foundCalleeTopics[2] = true;
                }

            } else if (topics.size() == 3) {

                if (log.address.equals(callerContract)) {
                    assertIsLogWithThreeTopics(callerContract, log);
                    foundCallerTopics[3] = true;
                } else {
                    assertIsLogWithThreeTopics(calleeContract, log);
                    foundCalleeTopics[3] = true;
                }

            } else if (topics.size() == 4) {

                if (log.address.equals(callerContract)) {
                    assertIsLogWithFourTopics(callerContract, log);
                    foundCallerTopics[4] = true;
                } else {
                    assertIsLogWithFourTopics(calleeContract, log);
                    foundCalleeTopics[4] = true;
                }

            } else {
                fail("Expected topic size to be in range [0,4] but was: " + topics.size());
            }
        }

        // Verify all of the 5 topic sizes were witnessed in the 5 logs by both caller and callee.
        for (boolean foundCallerTopic : foundCallerTopics) {
            assertTrue(foundCallerTopic);
        }
        for (boolean foundCalleeTopic : foundCalleeTopics) {
            assertTrue(foundCalleeTopic);
        }
    }

    private TransactionReceipt callMethodWriteLogsFromInternalCallAlso(Address caller, Address callee, byte[] data)
        throws Exception {
        RawTransaction transaction = makeCallTransaction(caller, "writeLogsFromInternalCallAlso", callee.getAddressBytes(), data);
        return sendTransaction(transaction);
    }

    private TransactionReceipt callMethodWriteAllLogs(Address contract)
        throws Exception {
        RawTransaction transaction = makeCallTransaction(contract, "writeAllLogs");
        return sendTransaction(transaction);
    }

    private TransactionReceipt callMethodWriteDataLogWithFourTopics(Address contract)
        throws Exception {
        RawTransaction transaction = makeCallTransaction(contract, "writeDataLogWithFourTopics");
        return sendTransaction(transaction);
    }

    private TransactionReceipt callMethodWriteDataLogWithThreeTopics(Address contract)
        throws Exception {
        RawTransaction transaction = makeCallTransaction(contract, "writeDataLogWithThreeTopics");
        return sendTransaction(transaction);
    }

    private TransactionReceipt callMethodWriteDataLogWithTwoTopics(Address contract)
        throws Exception {
        RawTransaction transaction = makeCallTransaction(contract, "writeDataLogWithTwoTopics");
        return sendTransaction(transaction);
    }

    private TransactionReceipt callMethodWriteDataLogWithOneTopic(Address contract)
        throws Exception {
        RawTransaction transaction = makeCallTransaction(contract, "writeDataLogWithOneTopic");
        return sendTransaction(transaction);
    }

    private TransactionReceipt callMethodWriteNoLogs(Address contract)
        throws Exception {
        RawTransaction transaction = makeCallTransaction(contract, "writeNoLogs");
        return sendTransaction(transaction);
    }

    private TransactionReceipt callMethodWriteDataOnlyLog(Address contract)
        throws Exception {
        RawTransaction transaction = makeCallTransaction(contract, "writeDataOnlyLog");
        return sendTransaction(transaction);
    }

    private static void assertIsDataOnlyLog(Address contract, TransactionLog log) {
        assertEquals(contract, log.address);
        assertArrayEquals(DATA, log.copyOfData());
        assertTrue(log.copyOfTopics().isEmpty());
    }

    private static void assertIsLogWithOneTopic(Address contract, TransactionLog log) {
        assertEquals(contract, log.address);
        assertArrayEquals(DATA, log.copyOfData());

        List<byte[]> topics = log.copyOfTopics();
        assertEquals(1, topics.size());
        assertArrayEquals(padOrTruncateTo32bytes(TOPIC1), topics.get(0));
    }

    private static void assertIsLogWithTwoTopics(Address contract, TransactionLog log) {
        assertEquals(contract, log.address);
        assertArrayEquals(DATA, log.copyOfData());

        List<byte[]> topics = log.copyOfTopics();
        assertEquals(2, topics.size());
        assertArrayEquals(padOrTruncateTo32bytes(TOPIC1), topics.get(0));
        assertArrayEquals(padOrTruncateTo32bytes(TOPIC2), topics.get(1));
    }

    private static void assertIsLogWithThreeTopics(Address contract, TransactionLog log) {
        assertEquals(contract, log.address);
        assertArrayEquals(DATA, log.copyOfData());

        List<byte[]> topics = log.copyOfTopics();
        assertEquals(3, topics.size());
        assertArrayEquals(padOrTruncateTo32bytes(TOPIC1), topics.get(0));
        assertArrayEquals(padOrTruncateTo32bytes(TOPIC2), topics.get(1));
        assertArrayEquals(padOrTruncateTo32bytes(TOPIC3), topics.get(2));
    }

    private static void assertIsLogWithFourTopics(Address contract, TransactionLog log) {
        assertEquals(contract, log.address);
        assertArrayEquals(DATA, log.copyOfData());

        List<byte[]> topics = log.copyOfTopics();
        assertEquals(4, topics.size());
        assertArrayEquals(padOrTruncateTo32bytes(TOPIC1), topics.get(0));
        assertArrayEquals(padOrTruncateTo32bytes(TOPIC2), topics.get(1));
        assertArrayEquals(padOrTruncateTo32bytes(TOPIC3), topics.get(2));
        assertArrayEquals(padOrTruncateTo32bytes(TOPIC4), topics.get(3));
    }

    private RawTransaction makeCallTransaction(Address contract, String method) throws Exception {
        IAvmStreamingEncoder encoder = TestHarnessAvmResources.avmUtility().newAvmStreamingEncoder(AvmVersion.VERSION_1);
        byte[] data = encoder.encodeOneString(method).toBytes();

        TransactionResult buildResult = RawTransaction.buildAndSignGeneralTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getAndIncrementNonce(),
            contract,
            data,
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO);

        assertTrue(buildResult.isSuccess());
        return buildResult.getTransaction();
    }

    private RawTransaction makeCallTransaction(Address contract, String method, byte[] internalContract, byte[] internalData) throws Exception {
        IAvmStreamingEncoder encoder = TestHarnessAvmResources.avmUtility().newAvmStreamingEncoder(AvmVersion.VERSION_1);
        byte[] data = encoder.encodeOneString(method).encodeOneAddress(new AionAddress(internalContract)).encodeOneByteArray(internalData).toBytes();

        TransactionResult buildResult = RawTransaction.buildAndSignGeneralTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getAndIncrementNonce(),
            contract,
            data,
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO);

        assertTrue(buildResult.isSuccess());
        return buildResult.getTransaction();
    }

    private Address deployLogTargetContract(AvmUtility avmUtility) throws InterruptedException, TimeoutException {
        TransactionResult result = RawTransaction.buildAndSignAvmCreateTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getAndIncrementNonce(),
            getAvmContractBytes(avmUtility),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO);
        assertTrue(result.isSuccess());

        TransactionReceipt receipt = sendTransaction(result.getTransaction());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        return receipt.getAddressOfDeployedContract().get();
    }

    private TransactionReceipt sendTransaction(RawTransaction transaction)
        throws InterruptedException, TimeoutException {
        // we want to ensure that the transaction gets sealed into a block.
        IEvent transactionIsSealed = prepackagedLogEventsFactory.build().getTransactionSealedEvent(transaction);
        IEvent transactionIsRejected = prepackagedLogEventsFactory.build().getTransactionRejectedEvent(transaction);
        IEvent transactionProcessed = Event.or(transactionIsSealed, transactionIsRejected);

        FutureResult<LogEventResult> future = listener.listenForEvent(transactionProcessed, 5, TimeUnit.MINUTES);

        // Send the transaction off.
        System.out.println("Sending the transaction...");
        RpcResult<ReceiptHash> sendResult = rpc.sendTransaction(transaction);
        assertRpcSuccess(sendResult);

        // Wait on the future to complete and ensure we saw the transaction get sealed.
        System.out.println("Waiting for the transaction to process...");
        LogEventResult listenResult = future.get(5, TimeUnit.MINUTES);
        assertTrue(listenResult.eventWasObserved());

        // Verify it was sealed and not rejected.
        assertFalse(transactionIsRejected.hasBeenObserved());
        assertTrue(transactionIsSealed.hasBeenObserved());
        System.out.println("Transaction was sealed into a block.");

        ReceiptHash hash = sendResult.getResult();

        RpcResult<TransactionReceipt> receiptResult = rpc.getTransactionReceipt(hash);
        assertRpcSuccess(receiptResult);

        return receiptResult.getResult();
    }

    private byte[] getAvmContractBytes(AvmUtility avmUtility) {
        return avmUtility.produceAvmJarBytes(AvmVersion.VERSION_1, AvmContract.HARNESS_LOG_TARGET);
    }

    private static byte[] padOrTruncateTo32bytes(byte[] bytes) {
        return Arrays.copyOf(bytes, 32);
    }
}
