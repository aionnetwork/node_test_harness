package org.aion.harness.tests.integ;

import static org.aion.harness.tests.contracts.Assertions.assertRpcSuccess;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;
import org.aion.avm.userlib.abi.ABIException;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.Event;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.tests.contracts.avm.InternalTxTarget;
import org.aion.harness.tests.integ.runner.SequentialRunner;
import org.aion.harness.tests.integ.runner.internal.LocalNodeListener;
import org.aion.harness.tests.integ.runner.internal.PreminedAccount;
import org.aion.harness.tests.integ.runner.internal.PrepackagedLogEventsFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SequentialRunner.class)
public class InternalTxTest {
    private static final long ENERGY_LIMIT = 1_233_567L;
    private static final long ENERGY_PRICE = 10_010_020_345L;

    private static RPC rpc = RPC.newRpc("127.0.0.1", "8545");

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();
    
    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();
    
    @Test
    public void testAvmContractPass() throws Exception {

        System.out.println("Deploying avm contract...");
        TransactionReceipt receipt = deployAvmContract();
        assertTrue(receipt.transactionWasSuccessful());
        Address avmContract = receipt.getAddressOfDeployedContract().get();
        assertNotNull(avmContract);

        System.out.println("Calling avm contract...");
        this.preminedAccount.incrementNonce();
        receipt = callAvmContract(avmContract, "callSelfToGetSix", 9, true);
        assertTrue(receipt.transactionWasSuccessful());
    }

    @Test
    public void testAvmContractFail() throws Exception {

        System.out.println("Deploying avm contract...");
        TransactionReceipt receipt = deployAvmContract();
        assertTrue(receipt.transactionWasSuccessful());
        Address avmContract = receipt.getAddressOfDeployedContract().get();
        assertNotNull(avmContract);

        System.out.println("Calling avm contract...");
        this.preminedAccount.incrementNonce();
        receipt = callAvmContract(avmContract, "callSelfToGetSix", 10, false);
        assertFalse(receipt.transactionWasSuccessful());
    }

    private TransactionReceipt deployAvmContract() throws InterruptedException, TimeoutException, InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = SignedTransaction.newAvmCreateTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getNonce(),
            getAvmContractBytes(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO, null);

        return sendDeployment(transaction);
    }

    private TransactionReceipt
    callAvmContract(Address contract, String methodName, int parameter, boolean shouldSucceed)
    throws InterruptedException, TimeoutException, InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        ABIStreamingEncoder encoder = new ABIStreamingEncoder();
        byte[] data = encoder.encodeOneString(methodName).encodeOneInteger(parameter).toBytes();
        SignedTransaction transaction = SignedTransaction.newGeneralTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getNonce(),
            contract,
            data,
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO, null);

        return
            shouldSucceed ? sendCallToSucceed(transaction) : sendCallToFail(transaction);
    }

    private TransactionReceipt sendDeployment(SignedTransaction transaction)
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

    private TransactionReceipt sendCallToSucceed(SignedTransaction transaction) throws InterruptedException {
        // we want to ensure that the transaction gets sealed into a block.
        IEvent transactionIsSealed = prepackagedLogEventsFactory.build().getTransactionSealedEvent(transaction);
        IEvent transactionIsRejected = prepackagedLogEventsFactory.build().getTransactionRejectedEvent(transaction);
        IEvent transactionProcessed = Event.or(transactionIsSealed, transactionIsRejected);
        IEvent depth9 = new Event("Internal depth of 9");
        FutureResult<LogEventResult> futureProcessed = listener.listenForEvent(transactionProcessed, 5, TimeUnit.MINUTES);
        FutureResult<LogEventResult> future9 = listener.listenForEvent(depth9, 5, TimeUnit.MINUTES);

        // Send the transaction off.
        System.out.println("Sending the transaction...");
        RpcResult<ReceiptHash> sendResult = rpc.sendSignedTransaction(transaction);
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

    private TransactionReceipt sendCallToFail(SignedTransaction transaction)
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
        RpcResult<ReceiptHash> sendResult = rpc.sendSignedTransaction(transaction);
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

    private byte[] getAvmContractBytes() {
        return new CodeAndArguments(JarBuilder.buildJarForMainAndClasses(InternalTxTarget.class,
            ABIDecoder.class, ABIEncoder.class, ABIException.class), new byte[0]).encodeToBytes();
    }
}
