package org.aion.harness.tests.integ;

import static org.aion.harness.tests.contracts.Assertions.assertRpcSuccess;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.Event;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.impl.JavaNode;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.tests.integ.runner.internal.LocalNodeListener;
import org.aion.harness.tests.integ.runner.internal.PreminedAccount;
import org.aion.harness.tests.integ.runner.internal.PrepackagedLogEventsFactory;
import org.aion.harness.util.SimpleLog;
import org.apache.commons.codec.binary.Hex;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests billing around data for the fvm before and after the 0.5.0 fork. Before the fork transactions
 * that data that is larger than their energy limit allows for will fail, after the fork these same
 * transactions will be rejected.
 */
public class FvmDataBillingTest {
    private static final long MIN_CALL_LIMIT = 21_000;
    private static final long MIN_CREATE_LIMIT = 221_000;
    private static final long ENERGY_PRICE = 10_000_000_000L;
    private IEvent transactionIsSealed;
    private IEvent transactionIsRejected;
    private static RPC rpc = RPC.newRpc("127.0.0.1", "8545");
    private final SimpleLog log = new SimpleLog(BeaconHashTest.class.getName());

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();

    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();

    @Test
    public void testPostForkBillingForCall() throws Exception {
        // Deploy a contract.
        Address contract = deployFvmContract();

        // Send the call transaction.
        byte[] data = allNonZeroBytes(50_000);
        SignedTransaction transaction = SignedTransaction.newGeneralTransaction(preminedAccount.getPrivateKey(), BigInteger.ONE, contract, data, MIN_CALL_LIMIT, ENERGY_PRICE, BigInteger.ZERO, null);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertNull(receipt);
        assertFalse(this.transactionIsSealed.hasBeenObserved());
    }

    @Test
    public void testPostForkBillingForCreate() throws Exception {

        byte[] data2 = allNonZeroBytes(50_000);
        SignedTransaction transaction2 = SignedTransaction.newGeneralTransaction(preminedAccount.getPrivateKey(), BigInteger.ZERO, null, data2, MIN_CREATE_LIMIT, ENERGY_PRICE, BigInteger.ZERO, null);
        TransactionReceipt receipt2 = sendTransaction(transaction2);
        assertNull(receipt2);
        assertFalse(this.transactionIsSealed.hasBeenObserved());
    }

    private Address deployFvmContract() throws Exception {
        String contractCodeAsString = "605060405234156100105760006000fd5b5b6101026000600050819090600019169055505b610029565b60e0806100376000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680636d4ce63c14603b578063ac6effc214606a576035565b60006000fd5b341560465760006000fd5b604c608f565b60405180826000191660001916815260100191505060405180910390f35b341560755760006000fd5b608d60048080356000191690601001909190505060a0565b005b60006000600050549050609d565b90565b806000600050819090600019169055505b505600a165627a7a72305820d6585e5cd5b612562558cd595d992e3cde87a53ed8765ab62c9662cca20d914a0029";
        byte[] contractCode = Hex.decodeHex(contractCodeAsString);

        SignedTransaction transaction = SignedTransaction.newGeneralTransaction(preminedAccount.getPrivateKey(), BigInteger.ZERO, null, contractCode, 2_000_000L, ENERGY_PRICE, BigInteger.ZERO, null);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertNotNull(receipt);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(this.transactionIsSealed.hasBeenObserved());
        assertFalse(this.transactionIsRejected.hasBeenObserved());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());

        return receipt.getAddressOfDeployedContract().get();
    }

    private TransactionReceipt sendTransaction(SignedTransaction transaction) throws InterruptedException, TimeoutException {
        // we want to ensure that the transaction gets sealed into a block.
        transactionIsSealed = prepackagedLogEventsFactory.build().getTransactionSealedEvent(transaction);
        transactionIsRejected = prepackagedLogEventsFactory.build().getTransactionRejectedEvent(transaction);
        IEvent transactionProcessed = Event.or(transactionIsSealed, transactionIsRejected);
        FutureResult<LogEventResult> future = this.listener.listenForEvent(transactionProcessed, 2, TimeUnit.MINUTES);

        // Send the transaction off.
        System.out.println("Sending the transaction...");
        RpcResult<ReceiptHash> sendResult = rpc.sendSignedTransaction(transaction);

        if (!sendResult.isSuccess()) {
            return null;
        }

        // Wait on the future to complete and ensure we saw the transaction get sealed.
        System.out.println("Waiting for the transaction to process...");
        LogEventResult listenResult = future.get(2, TimeUnit.MINUTES);
        assertTrue(listenResult.eventWasObserved());
        System.out.println("Transaction was processed!");

        ReceiptHash hash = sendResult.getResult();

        RpcResult<TransactionReceipt> receiptResult = rpc.getTransactionReceipt(hash);
        assertRpcSuccess(receiptResult);

        return receiptResult.getResult();
    }

    private static byte[] allNonZeroBytes(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) 0xff;
        }
        return bytes;
    }
}
