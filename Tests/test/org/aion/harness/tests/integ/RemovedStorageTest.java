package org.aion.harness.tests.integ;

import static org.aion.harness.tests.contracts.Assertions.assertRpcSuccess;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.ABIUtil;
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
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.tests.contracts.avm.RemoveStorageTarget;
import org.aion.harness.tests.contracts.avm.StorageTargetClinitTarget;
import org.aion.harness.tests.integ.runner.RunWithNodes;
import org.aion.harness.tests.integ.runner.SequentialRunner;
import org.aion.harness.tests.integ.runner.internal.LocalNodeListener;
import org.aion.harness.tests.integ.runner.internal.PreminedAccount;
import org.aion.harness.tests.integ.runner.internal.PrepackagedLogEventsFactory;
import org.aion.harness.util.SimpleLog;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SequentialRunner.class)
@RunWithNodes(NodeType.JAVA_NODE)
public class RemovedStorageTest {
    private static final long ENERGY_LIMIT = 1_234_567L;
    private static final long ENERGY_PRICE = 10_010_020_345L;

    private final SimpleLog log = new SimpleLog(this.getClass().getName());

    private static RPC rpc = RPC.newRpc("127.0.0.1", "8545");

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();

    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();

    @Test
    public void putResetVerifyStatic() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        SignedTransaction transaction = makeCallTransaction(contract, "putStatic");
        receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());

        transaction = makeCallTransaction(contract, "resetStatic");
        receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());

        transaction = makeCallTransaction(contract, "verifyStatic");
        receipt = sendGetStaticTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    @Test
    public void putResetPut() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putResetPut(contract);
    }

    @Test
    public void putZeroResetVerify() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putStorageLengthZero(contract);
        resetStorage(contract);
        verifyAllStorageRemoved(contract);
    }

    @Test
    public void putZeroResetResetVerify() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putStorageLengthZero(contract);
        resetStorage(contract);
        resetStorage(contract);
        verifyAllStorageRemoved(contract);
    }

    @Test
    public void putOneResetVerify() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putStorageLengthOne(contract);
        resetStorage(contract);
        verifyAllStorageRemoved(contract);
    }

    @Test
    public void putOneResetResetVerify() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putStorageLengthOne(contract);
        resetStorage(contract);
        resetStorage(contract);
        verifyAllStorageRemoved(contract);
    }

    @Test
    public void resetResetVerify() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        resetStorage(contract);
        resetStorage(contract);
        verifyAllStorageRemoved(contract);
    }

    @Test
    public void resetVerify() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        resetStorage(contract);
        verifyAllStorageRemoved(contract);
    }

    @Test
    public void putZeroVerify() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putStorageLengthZero(contract);
        validateStoragePreviousTxLength(contract, 0);
    }

    @Test
    public void putOneVerify() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putStorageLengthOne(contract);
        validateStoragePreviousTxLength(contract, 1);
    }

    @Test
    public void putResetVerify() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putStorageAddress(contract);
        resetStorage(contract);
        verifyAllStorageRemoved(contract);
    }

    @Test
    public void putAddressVerify() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putStorageAddress(contract);
        validateStoragePreviousTxLength(contract, 32);
    }

    @Test
    public void putAddressResetResetVerify() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putStorageAddress(contract);
        resetStorage(contract);
        resetStorage(contract);
        verifyAllStorageRemoved(contract);
    }

    @Test
    public void putArbitrarySameKeyVerify() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        byte[] b = new byte[]{ 0, 1, 2, 3, 4, 5 };
        setStorageSameKey(contract, b);
        getStorageOneKey(contract, b.length);
    }

    @Test
    public void putZeroSameKeyVerify() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        byte[] b = new byte[0];
        setStorageSameKey(contract, b);
        getStorageOneKey(contract, b.length);
    }

    @Test
    public void putOneSameKeyVerify() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        byte[] b = new byte[] { 0 };
        setStorageSameKey(contract, b);
        getStorageOneKey(contract, b.length);
    }

    @Test
    public void putNullSameKeyVerify() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        setStorageSameKey(contract, null);
        getStorageOneKey(contract, -1);
    }

    @Test
    public void putArbitrarySameKeyVerifyNullSameKeyVerify() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        byte[] b = new byte[]{ 0, 1, 2, 3, 4, 5 };
        setStorageSameKey(contract, b);
        getStorageOneKey(contract, b.length);
        setStorageSameKey(contract, null);
        getStorageOneKey(contract, -1);
    }

    @Test
    public void putZeroSameKeyVerifyNullSameKeyVerify() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        byte[] b = new byte[0];
        setStorageSameKey(contract, b);
        getStorageOneKey(contract, b.length);
        setStorageSameKey(contract, null);
        getStorageOneKey(contract, -1);
    }

    @Test
    public void putOneSameKeyVerifyNullSameKeyVerify() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        byte[] b = new byte[]{ 0 };
        setStorageSameKey(contract, b);
        getStorageOneKey(contract, b.length);
        setStorageSameKey(contract, null);
        getStorageOneKey(contract, -1);
    }

    @Test
    public void removeStorageClinit() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateClinitTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
    }

    @Test
    public void removeStorageReentrant() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        byte[] b = new ABIStreamingEncoder().encodeOneString("resetStorage").toBytes();
        reentrantCallAfterPut(contract, b);
    }

    @Test
    public void multipleGetSetVerifiesInSameCall() throws Exception {
        SignedTransaction deployTransaction = makeAvmCreateTransaction();
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putZeroResetVerify(contract);
        putOneResetVerify(contract);
        putAddressResetVerify(contract);
        ResetResetVerify(contract);

    }

    private void putStorageLengthZero(Address contract)
        throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "putStorageLengthZero");
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void resetStorage(Address contract) throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "resetStorage");
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void verifyAllStorageRemoved(Address contract)
        throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "verifyAllStorageRemoved");
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void putStorageLengthOne(Address contract) throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "putStorageLengthOne");
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void putResetPut(Address contract) throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "putResetPut");
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void putStorageAddress(Address contract) throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "putStorageAddress");
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void validateStoragePreviousTxLength(Address contract, int i)
        throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "validateStoragePreviousTxLength", i);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void getStorageOneKey(Address contract, int i)
        throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "getStorageOneKey", i);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void setStorageSameKey(Address contract, byte[] b)
        throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "setStorageSameKey", b);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void reentrantCallAfterPut(Address contract, byte[] b)
        throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "reentrantCallAfterPut", b);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void putZeroResetVerify(Address contract) throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "putZeroResetVerify");
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void putOneResetVerify(Address contract) throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "putOneResetVerify");
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void putAddressResetVerify(Address contract)
        throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "putAddressResetVerify");
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void ResetResetVerify(Address contract) throws InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = makeCallTransaction(contract, "ResetResetVerify");
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private TransactionReceipt sendTransaction(SignedTransaction transaction)
        throws InterruptedException, TimeoutException {
        // we want to ensure that the transaction gets sealed into a block.
        IEvent transactionIsSealed = prepackagedLogEventsFactory.build().getTransactionSealedEvent(transaction);
        IEvent transactionIsRejected = prepackagedLogEventsFactory.build().getTransactionRejectedEvent(transaction);
        IEvent event = Event.or(transactionIsRejected, transactionIsSealed);
        FutureResult<LogEventResult> future = this.listener.listenForEvent(event, 5, TimeUnit.MINUTES);

        // Send the transaction off.
        RpcResult<ReceiptHash> sendResult = rpc.sendSignedTransaction(transaction);
        assertRpcSuccess(sendResult);

        // Wait on the future to complete and ensure we saw the transaction get sealed.
        LogEventResult listenResult = future.get(5, TimeUnit.MINUTES);
        assertTrue(listenResult.eventWasObserved());
        assertTrue(transactionIsSealed.hasBeenObserved());
        assertFalse(transactionIsRejected.hasBeenObserved());

        ReceiptHash hash = sendResult.getResult();

        RpcResult<TransactionReceipt> receiptResult = rpc.getTransactionReceipt(hash);
        assertRpcSuccess(receiptResult);
        return receiptResult.getResult();
    }

    private TransactionReceipt sendGetStaticTransaction(SignedTransaction transaction)
        throws InterruptedException, TimeoutException {
        // we want to ensure that the transaction gets sealed into a block.
        IEvent transactionIsSealed = prepackagedLogEventsFactory.build().getTransactionSealedEvent(transaction);
        IEvent transactionIsRejected = prepackagedLogEventsFactory.build().getTransactionRejectedEvent(transaction);
        IEvent transactionIsProcessed = Event.or(transactionIsSealed, transactionIsRejected);
        IEvent verifyIsCorrect = new Event("CORRECT: found null");
        IEvent verifyIsIncorrect = new Event("INCORRECT: found non-null");
        IEvent verifyEvent = Event.or(verifyIsCorrect, verifyIsIncorrect);
        IEvent transactionProcessedAndVerified = Event.and(transactionIsProcessed, verifyEvent);

        FutureResult<LogEventResult> future = this.listener.listenForEvent(transactionProcessedAndVerified, 5, TimeUnit.MINUTES);

        // Send the transaction off.
        RpcResult<ReceiptHash> sendResult = rpc.sendSignedTransaction(transaction);
        assertRpcSuccess(sendResult);

        // Wait on the future to complete and ensure we saw the transaction get sealed.
        LogEventResult listenResult = future.get(5, TimeUnit.MINUTES);
        assertTrue(listenResult.eventWasObserved());

        assertTrue(transactionIsSealed.hasBeenObserved());
        assertFalse(transactionIsRejected.hasBeenObserved());
        assertTrue(verifyIsCorrect.hasBeenObserved());
        assertFalse(verifyIsIncorrect.hasBeenObserved());

        ReceiptHash hash = sendResult.getResult();

        RpcResult<TransactionReceipt> receiptResult = rpc.getTransactionReceipt(hash);
        assertRpcSuccess(receiptResult);
        return receiptResult.getResult();
    }

    private SignedTransaction makeCallTransaction(Address contract, String method, byte[] b) throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        byte[] data = new ABIStreamingEncoder().encodeOneString(method).encodeOneByteArray(b).toBytes();

        return SignedTransaction.newGeneralTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getAndIncrementNonce(),
            contract,
            data,
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO, null);
    }

    private SignedTransaction makeCallTransaction(Address contract, String method, int i) throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        byte[] data = ABIUtil.encodeMethodArguments(method, i);

        return SignedTransaction.newGeneralTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getAndIncrementNonce(),
            contract,
            data,
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO, null);
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

    private SignedTransaction makeAvmCreateTransaction() throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        return SignedTransaction.newAvmCreateTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getAndIncrementNonce(),
            new CodeAndArguments(JarBuilder.buildJarForMainAndClasses(RemoveStorageTarget.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO, null);
    }

    private SignedTransaction makeAvmCreateClinitTransaction() throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        return SignedTransaction.newAvmCreateTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getAndIncrementNonce(),
            new CodeAndArguments(JarBuilder.buildJarForMainAndClasses(StorageTargetClinitTarget.class, ABIDecoder.class, ABIException.class, ABIToken.class), new byte[0]).encodeToBytes(),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO, null);
    }
}
