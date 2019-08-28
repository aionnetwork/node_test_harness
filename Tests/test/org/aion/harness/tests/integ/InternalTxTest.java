package org.aion.harness.tests.integ;

import static org.aion.harness.tests.contracts.Assertions.assertRpcSuccess;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
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
import org.aion.vm.AvmUtility;
import org.aion.vm.AvmVersion;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SequentialRunner.class)
@ExcludeNodeType(NodeType.RUST_NODE) // exclude Rust for now due to bugs that prevent tests from passing
public class InternalTxTest {
    private static final long ENERGY_LIMIT = 1_233_567L;
    private static final long ENERGY_PRICE = 10_010_020_345L;

    private static RPC rpc = new RPC("127.0.0.1", "8545");

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();
    
    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();
    
    @Test
    public void testAvmContractPass() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        System.out.println("Deploying avm contract...");
        TransactionReceipt receipt = deployAvmContract(avmUtility);
        assertTrue(receipt.transactionWasSuccessful());
        Address avmContract = receipt.getAddressOfDeployedContract().get();
        assertNotNull(avmContract);

        System.out.println("Calling avm contract...");
        this.preminedAccount.incrementNonce();
        receipt = callAvmContract(avmContract, "callSelfToGetSix", 9, true, avmUtility);
        assertTrue(receipt.transactionWasSuccessful());
    }

    @Test
    public void testAvmContractFail() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        System.out.println("Deploying avm contract...");
        TransactionReceipt receipt = deployAvmContract(avmUtility);
        assertTrue(receipt.transactionWasSuccessful());
        Address avmContract = receipt.getAddressOfDeployedContract().get();
        assertNotNull(avmContract);

        System.out.println("Calling avm contract...");
        this.preminedAccount.incrementNonce();
        receipt = callAvmContract(avmContract, "callSelfToGetSix", 10, false, avmUtility);
        assertFalse(receipt.transactionWasSuccessful());
    }

    private TransactionReceipt deployAvmContract(AvmUtility avmUtility) throws InterruptedException, TimeoutException {
        TransactionResult result = RawTransaction.buildAndSignAvmCreateTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getNonce(),
            getAvmContractBytes(avmUtility),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO);
        assertTrue(result.isSuccess());

        return sendDeployment(result.getTransaction());
    }

    private TransactionReceipt
    callAvmContract(Address contract, String methodName, int parameter, boolean shouldSucceed, AvmUtility avmUtility)
    throws InterruptedException, TimeoutException {
        IAvmStreamingEncoder encoder = avmUtility.newAvmStreamingEncoder(AvmVersion.VERSION_1);
        byte[] data = encoder.encodeOneString(methodName).encodeOneInteger(parameter).toBytes();
        TransactionResult result = RawTransaction.buildAndSignGeneralTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getNonce(),
            contract,
            data,
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO);
        assertTrue(result.isSuccess());

        return
            shouldSucceed ? sendCallToSucceed(result.getTransaction()) : sendCallToFail(result.getTransaction());
    }

    private TransactionReceipt sendDeployment(RawTransaction transaction)
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

    private TransactionReceipt sendCallToSucceed(RawTransaction transaction) throws InterruptedException {
        // we want to ensure that the transaction gets sealed into a block.
        IEvent transactionIsSealed = prepackagedLogEventsFactory.build().getTransactionSealedEvent(transaction);
        IEvent transactionIsRejected = prepackagedLogEventsFactory.build().getTransactionRejectedEvent(transaction);
        IEvent transactionProcessed = Event.or(transactionIsSealed, transactionIsRejected);
        IEvent depth9 = new Event("Internal depth of 9");
        FutureResult<LogEventResult> futureProcessed = listener.listenForEvent(transactionProcessed, 5, TimeUnit.MINUTES);
        FutureResult<LogEventResult> future9 = listener.listenForEvent(depth9, 5, TimeUnit.MINUTES);

        // Send the transaction off.
        System.out.println("Sending the transaction...");
        RpcResult<ReceiptHash> sendResult = rpc.sendTransaction(transaction);
        assertRpcSuccess(sendResult);

        // Wait on the future to complete and ensure we saw the transaction get sealed.
        System.out.println("Waiting for the transaction to process...");
        LogEventResult listenResult = null;
        try {
            listenResult = futureProcessed.get(5, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
        assertTrue(listenResult.eventWasObserved());
        listenResult = future9.get();
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

    private TransactionReceipt sendCallToFail(RawTransaction transaction)
    throws InterruptedException, TimeoutException {
        // we want to ensure that the transaction gets sealed into a block.
        IEvent transactionIsSealed = prepackagedLogEventsFactory.build().getTransactionSealedEvent(transaction);
        IEvent transactionIsRejected = prepackagedLogEventsFactory.build().getTransactionRejectedEvent(transaction);
        IEvent transactionProcessed = Event.or(transactionIsSealed, transactionIsRejected);
        IEvent depth9 = new Event("Internal depth of 9");
        IEvent depth10 = new Event("Failed at depth 10");
        FutureResult<LogEventResult> futureProcessed = listener.listenForEvent(transactionProcessed, 5, TimeUnit.MINUTES);
        FutureResult<LogEventResult> future9 = listener.listenForEvent(depth9, 5, TimeUnit.MINUTES);
        FutureResult<LogEventResult> future10 = listener.listenForEvent(depth10, 5, TimeUnit.MINUTES);

        // Send the transaction off.
        System.out.println("Sending the transaction...");
        RpcResult<ReceiptHash> sendResult = rpc.sendTransaction(transaction);
        assertRpcSuccess(sendResult);

        // Wait on the future to complete and ensure we saw the transaction get sealed.
        System.out.println("Waiting for the transaction to process...");
        LogEventResult listenResult = futureProcessed.get(5, TimeUnit.MINUTES);
        assertTrue(listenResult.eventWasObserved());
        listenResult = future9.get(5, TimeUnit.MINUTES);
        assertTrue(listenResult.eventWasObserved());
        listenResult = future10.get(5, TimeUnit.MINUTES);
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
        return avmUtility.produceAvmJarBytes(AvmVersion.VERSION_1, AvmContract.HARNESS_INTERNAL_CALL);
    }
}
