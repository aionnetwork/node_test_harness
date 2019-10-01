package org.aion.harness.tests.integ;

import static org.aion.harness.tests.contracts.Assertions.assertRpcSuccess;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;
import org.aion.avm.userlib.abi.ABIException;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.avm.userlib.abi.ABIToken;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.SignedTransaction;
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
import org.aion.harness.tests.contracts.avm.LogTarget;
import org.aion.harness.tests.integ.runner.RunWithNodes;
import org.aion.harness.tests.integ.runner.SequentialRunner;
import org.aion.harness.tests.integ.runner.internal.LocalNodeListener;
import org.aion.harness.tests.integ.runner.internal.PreminedAccount;
import org.aion.harness.tests.integ.runner.internal.PrepackagedLogEventsFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests Avm contract logs -- particularly the logs that appear in the transaction receipt. Logs
 * can be queried in other ways..
 */
@RunWith(SequentialRunner.class)
@RunWithNodes(NodeType.JAVA_NODE)
public class AvmReceiptLogTest {
    private static final long ENERGY_LIMIT = 1_234_567L;
    private static final long ENERGY_PRICE = 10_010_020_345L;

    private static RPC rpc = RPC.newRpc("127.0.0.1", "8545");

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();

    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();

    @Test
    public void testContractWritesNoLogs() throws Exception {
        Address contract = deployLogTargetContract();

        TransactionReceipt receipt = callMethodWriteNoLogs(contract);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getLogs().isEmpty());
    }

    @Test
    public void testContractWritesDataOnlyLog() throws Exception {
        Address contract = deployLogTargetContract();

        TransactionReceipt receipt = callMethodWriteDataOnlyLog(contract);
        assertTrue(receipt.transactionWasSuccessful());
        List<TransactionLog> logs = receipt.getLogs();

        assertEquals(1, logs.size());

        TransactionLog log = logs.get(0);
        assertIsDataOnlyLog(contract, log);
    }

    @Test
    public void testContractWritesLogWithOneTopic() throws Exception {
        Address contract = deployLogTargetContract();

        TransactionReceipt receipt = callMethodWriteDataLogWithOneTopic(contract);
        assertTrue(receipt.transactionWasSuccessful());
        List<TransactionLog> logs = receipt.getLogs();

        assertEquals(1, logs.size());

        TransactionLog log = logs.get(0);
        assertIsLogWithOneTopic(contract, log);
    }

    @Test
    public void testContractWritesLogWithTwoTopics() throws Exception {
        Address contract = deployLogTargetContract();

        TransactionReceipt receipt = callMethodWriteDataLogWithTwoTopics(contract);
        assertTrue(receipt.transactionWasSuccessful());
        List<TransactionLog> logs = receipt.getLogs();

        assertEquals(1, logs.size());

        TransactionLog log = logs.get(0);
        assertIsLogWithTwoTopics(contract, log);
    }

    @Test
    public void testContractWritesLogWithThreeTopics() throws Exception {
        Address contract = deployLogTargetContract();

        TransactionReceipt receipt = callMethodWriteDataLogWithThreeTopics(contract);
        assertTrue(receipt.transactionWasSuccessful());
        List<TransactionLog> logs = receipt.getLogs();

        assertEquals(1, logs.size());

        TransactionLog log = logs.get(0);
        assertIsLogWithThreeTopics(contract, log);
    }

    @Test
    public void testContractWritesLogWithFourTopics() throws Exception {
        Address contract = deployLogTargetContract();

        TransactionReceipt receipt = callMethodWriteDataLogWithFourTopics(contract);
        assertTrue(receipt.transactionWasSuccessful());
        List<TransactionLog> logs = receipt.getLogs();

        assertEquals(1, logs.size());

        TransactionLog log = logs.get(0);
        assertIsLogWithFourTopics(contract, log);
    }

    @Test
    public void testContractWritesMultipleLogs() throws Exception {
        Address contract = deployLogTargetContract();

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
        Address callerContract = deployLogTargetContract();
        Address calleeContract = deployLogTargetContract();

        // The internal call will invoke the writeAllLogs method.
        byte[] internalCallData = new ABIStreamingEncoder().encodeOneString("writeAllLogs").toBytes();
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
        throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(caller, "writeLogsFromInternalCallAlso", callee.getAddressBytes(), data);
        return sendTransaction(transaction);
    }

    private TransactionReceipt callMethodWriteAllLogs(Address contract)
        throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "writeAllLogs");
        return sendTransaction(transaction);
    }

    private TransactionReceipt callMethodWriteDataLogWithFourTopics(Address contract)
        throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "writeDataLogWithFourTopics");
        return sendTransaction(transaction);
    }

    private TransactionReceipt callMethodWriteDataLogWithThreeTopics(Address contract)
        throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "writeDataLogWithThreeTopics");
        return sendTransaction(transaction);
    }

    private TransactionReceipt callMethodWriteDataLogWithTwoTopics(Address contract)
        throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "writeDataLogWithTwoTopics");
        return sendTransaction(transaction);
    }

    private TransactionReceipt callMethodWriteDataLogWithOneTopic(Address contract)
        throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "writeDataLogWithOneTopic");
        return sendTransaction(transaction);
    }

    private TransactionReceipt callMethodWriteNoLogs(Address contract)
        throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "writeNoLogs");
        return sendTransaction(transaction);
    }

    private TransactionReceipt callMethodWriteDataOnlyLog(Address contract)
        throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "writeDataOnlyLog");
        return sendTransaction(transaction);
    }

    private static void assertIsDataOnlyLog(Address contract, TransactionLog log) {
        assertEquals(contract, log.address);
        assertArrayEquals(LogTarget.data, log.copyOfData());
        assertTrue(log.copyOfTopics().isEmpty());
    }

    private static void assertIsLogWithOneTopic(Address contract, TransactionLog log) {
        assertEquals(contract, log.address);
        assertArrayEquals(LogTarget.data, log.copyOfData());

        List<byte[]> topics = log.copyOfTopics();
        assertEquals(1, topics.size());
        assertArrayEquals(padOrTruncateTo32bytes(LogTarget.topic1), topics.get(0));
    }

    private static void assertIsLogWithTwoTopics(Address contract, TransactionLog log) {
        assertEquals(contract, log.address);
        assertArrayEquals(LogTarget.data, log.copyOfData());

        List<byte[]> topics = log.copyOfTopics();
        assertEquals(2, topics.size());
        assertArrayEquals(padOrTruncateTo32bytes(LogTarget.topic1), topics.get(0));
        assertArrayEquals(padOrTruncateTo32bytes(LogTarget.topic2), topics.get(1));
    }

    private static void assertIsLogWithThreeTopics(Address contract, TransactionLog log) {
        assertEquals(contract, log.address);
        assertArrayEquals(LogTarget.data, log.copyOfData());

        List<byte[]> topics = log.copyOfTopics();
        assertEquals(3, topics.size());
        assertArrayEquals(padOrTruncateTo32bytes(LogTarget.topic1), topics.get(0));
        assertArrayEquals(padOrTruncateTo32bytes(LogTarget.topic2), topics.get(1));
        assertArrayEquals(padOrTruncateTo32bytes(LogTarget.topic3), topics.get(2));
    }

    private static void assertIsLogWithFourTopics(Address contract, TransactionLog log) {
        assertEquals(contract, log.address);
        assertArrayEquals(LogTarget.data, log.copyOfData());

        List<byte[]> topics = log.copyOfTopics();
        assertEquals(4, topics.size());
        assertArrayEquals(padOrTruncateTo32bytes(LogTarget.topic1), topics.get(0));
        assertArrayEquals(padOrTruncateTo32bytes(LogTarget.topic2), topics.get(1));
        assertArrayEquals(padOrTruncateTo32bytes(LogTarget.topic3), topics.get(2));
        assertArrayEquals(padOrTruncateTo32bytes(LogTarget.topic4), topics.get(3));
    }

    private SignedTransaction makeCallTransaction(Address contract, String method) throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        byte[] data = ABIEncoder.encodeOneString(method);

        return SignedTransaction.newGeneralTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getAndIncrementNonce(),
            contract,
            data,
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO, null);
    }

    private SignedTransaction makeCallTransaction(Address contract, String method, byte[] internalContract, byte[] internalData) throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        byte[] data = new ABIStreamingEncoder().encodeOneString(method).encodeOneAddress(new avm.Address(internalContract)).encodeOneByteArray(internalData).toBytes();

        return SignedTransaction.newGeneralTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getAndIncrementNonce(),
            contract,
            data,
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO, null);
    }

    private Address deployLogTargetContract() throws InterruptedException, TimeoutException, InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = SignedTransaction.newAvmCreateTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getAndIncrementNonce(),
            getAvmContractBytes(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO, null);

        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        return receipt.getAddressOfDeployedContract().get();
    }

    private TransactionReceipt sendTransaction(SignedTransaction transaction)
        throws InterruptedException, TimeoutException {
        // we want to ensure that the transaction gets sealed into a block.
        IEvent transactionIsSealed = prepackagedLogEventsFactory.build().getTransactionSealedEvent(transaction);
        IEvent transactionIsRejected = prepackagedLogEventsFactory.build().getTransactionRejectedEvent(transaction);
        IEvent transactionProcessed = Event.or(transactionIsSealed, transactionIsRejected);

        FutureResult<LogEventResult> future = listener.listenForEvent(transactionProcessed, 5, TimeUnit.MINUTES);

        // Send the transaction off.
        System.out.println("Sending the transaction...");
        RpcResult<ReceiptHash> sendResult = rpc.sendSignedTransaction(transaction);
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

    private byte[] getAvmContractBytes() {
        return new CodeAndArguments(JarBuilder.buildJarForMainAndClasses(LogTarget.class, ABIDecoder.class, ABIToken.class, ABIException.class), new byte[0]).encodeToBytes();
    }

    private static byte[] padOrTruncateTo32bytes(byte[] bytes) {
        return Arrays.copyOf(bytes, 32);
    }
}
