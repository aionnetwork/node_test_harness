package org.aion.harness.tests.integ;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.tests.contracts.Assertions;
import org.aion.harness.tests.contracts.avm.AvmFailureModes;
import org.aion.harness.tests.integ.runner.SequentialRunner;
import org.aion.harness.tests.integ.runner.internal.LocalNodeListener;
import org.aion.harness.tests.integ.runner.internal.PreminedAccount;
import org.aion.harness.tests.integ.runner.internal.PrepackagedLogEventsFactory;
import org.aion.harness.util.SimpleLog;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Tests the behaviour of various AVM success/failure modes, checking that the consumed energy is consistent with expectations.
 */
@RunWith(SequentialRunner.class)
public class AvmFailuresTest {
    private static final long ENERGY_LIMIT = 2_000_000L;
    private static final long ENERGY_PRICE = 10_010_020_345L;

    private final SimpleLog log = new SimpleLog("org.aion.harness.tests.integ.AvmFailuresTest");

    private static RPC rpc = RPC.newRpc("127.0.0.1", "8545");

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();

    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();

    @Test
    public void testPass() throws Exception {
        Address contract = deployContract();
        
        // Send the "pass" (0).
        TransactionReceipt receipt = sendTransactionToContract(contract, (byte)0);
        boolean wasSuccess = receipt.transactionWasSuccessful();
        long energyConsumed = receipt.getTransactionEnergyConsumed();
        Assert.assertTrue(wasSuccess);
        Assert.assertEquals(21378, energyConsumed);
    }

    @Test
    public void testFailException() throws Exception {
        Address contract = deployContract();
        
        // Send the "fail - exception" (1).
        TransactionReceipt receipt = sendTransactionToContract(contract, (byte)1);
        boolean wasSuccess = receipt.transactionWasSuccessful();
        long energyConsumed = receipt.getTransactionEnergyConsumed();
        Assert.assertFalse(wasSuccess);
        Assert.assertEquals(ENERGY_LIMIT, energyConsumed);
    }

    @Test
    public void testFailRevert() throws Exception {
        Address contract = deployContract();
        
        // Send the "fail - revert" (2).
        TransactionReceipt receipt = sendTransactionToContract(contract, (byte)2);
        boolean wasSuccess = receipt.transactionWasSuccessful();
        long energyConsumed = receipt.getTransactionEnergyConsumed();
        Assert.assertFalse(wasSuccess);
        Assert.assertEquals(21378, energyConsumed);
    }

    @Test
    public void testFailEnergy() throws Exception {
        Address contract = deployContract();
        
        // Send the "fail - out of energy" (3).
        TransactionReceipt receipt = sendTransactionToContract(contract, (byte)3);
        boolean wasSuccess = receipt.transactionWasSuccessful();
        long energyConsumed = receipt.getTransactionEnergyConsumed();
        Assert.assertFalse(wasSuccess);
        Assert.assertEquals(ENERGY_LIMIT, energyConsumed);
    }


    private Address deployContract() throws InterruptedException, TimeoutException, InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        // build contract deployment Tx
        SignedTransaction transaction = SignedTransaction.newAvmCreateTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getAndIncrementNonce(),
            new CodeAndArguments(
                JarBuilder.buildJarForMainAndClasses(AvmFailureModes.class), new byte[0])
                .encodeToBytes(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO, /* amount */
            null);
        
        // send contract deployment Tx
        TransactionReceipt deployReceipt = sendRawTransactionSynchronously(transaction);
        Assert.assertTrue(deployReceipt.getAddressOfDeployedContract().isPresent());
        return deployReceipt.getAddressOfDeployedContract().get();
    }

    private TransactionReceipt sendTransactionToContract(Address contract, byte argument)
        throws InterruptedException, TimeoutException, InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        // Create the transaction.
        SignedTransaction transaction = SignedTransaction.newGeneralTransaction(
                this.preminedAccount.getPrivateKey(),
                this.preminedAccount.getAndIncrementNonce(),
                contract,
                new byte[] {argument},
                ENERGY_LIMIT,
                ENERGY_PRICE,
                BigInteger.ZERO,
            null);
        
        return sendRawTransactionSynchronously(transaction);
    }

    private TransactionReceipt sendRawTransactionSynchronously(SignedTransaction rawTransaction)
        throws InterruptedException, TimeoutException {
        // Capture the event for asynchronous waiting.
        IEvent transactionIsSealed = prepackagedLogEventsFactory.build().getTransactionSealedEvent(rawTransaction);
        FutureResult<LogEventResult> future = this.listener.listenForEvent(transactionIsSealed, 5, TimeUnit.MINUTES);
        
        // Send the transaction.
        RpcResult<ReceiptHash> sendResult = AvmFailuresTest.rpc.sendSignedTransaction(rawTransaction);
        Assertions.assertRpcSuccess(sendResult);
        
        // Wait for this to be mined.
        waitForEvent(future);
        
        // Get the receipt (depends on it being mined).
        ReceiptHash hash = sendResult.getResult();
        RpcResult<TransactionReceipt> receiptResult = AvmFailuresTest.rpc.getTransactionReceipt(hash);
        Assertions.assertRpcSuccess(receiptResult);
        return receiptResult.getResult();
    }

    private void waitForEvent(FutureResult<LogEventResult> future)
        throws InterruptedException, TimeoutException {
        log.log("Waiting for the transaction to process...");
        LogEventResult listenResult = future.get(5, TimeUnit.MINUTES);
        Assert.assertTrue(listenResult.eventWasObserved());
        log.log("Transaction was sealed into a block.");
    }
}
