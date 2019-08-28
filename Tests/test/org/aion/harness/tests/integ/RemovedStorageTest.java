package org.aion.harness.tests.integ;

import static org.aion.harness.tests.contracts.Assertions.assertRpcSuccess;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aion.avm.common.AvmContract;
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
import org.aion.harness.util.SimpleLog;
import org.aion.vm.AvmUtility;
import org.aion.vm.AvmVersion;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SequentialRunner.class)
@ExcludeNodeType(NodeType.RUST_NODE) // exclude Rust for now due to bugs that prevent tests from passing
public class RemovedStorageTest {
    private static final long ENERGY_LIMIT = 1_234_567L;
    private static final long ENERGY_PRICE = 10_010_020_345L;

    private final SimpleLog log = new SimpleLog(this.getClass().getName());

    private static RPC rpc = new RPC("127.0.0.1", "8545");

    @Rule
    private PreminedAccount preminedAccount = new PreminedAccount(BigInteger.valueOf(1_000_000_000_000_000_000L));

    @Rule
    private LocalNodeListener listener = new LocalNodeListener();

    @Rule
    private PrepackagedLogEventsFactory prepackagedLogEventsFactory = new PrepackagedLogEventsFactory();

    @Test
    public void putResetVerifyStatic() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        RawTransaction transaction = makeCallTransaction(contract, "putStatic", avmUtility);
        receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());

        transaction = makeCallTransaction(contract, "resetStatic", avmUtility);
        receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());

        transaction = makeCallTransaction(contract, "verifyStatic", avmUtility);
        receipt = sendGetStaticTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    @Test
    public void putResetPut() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putResetPut(contract, avmUtility);
    }

    @Test
    public void putZeroResetVerify() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putStorageLengthZero(contract, avmUtility);
        resetStorage(contract, avmUtility);
        verifyAllStorageRemoved(contract, avmUtility);
    }

    @Test
    public void putZeroResetResetVerify() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putStorageLengthZero(contract, avmUtility);
        resetStorage(contract, avmUtility);
        resetStorage(contract, avmUtility);
        verifyAllStorageRemoved(contract, avmUtility);
    }

    @Test
    public void putOneResetVerify() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putStorageLengthOne(contract, avmUtility);
        resetStorage(contract, avmUtility);
        verifyAllStorageRemoved(contract, avmUtility);
    }

    @Test
    public void putOneResetResetVerify() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putStorageLengthOne(contract, avmUtility);
        resetStorage(contract, avmUtility);
        resetStorage(contract, avmUtility);
        verifyAllStorageRemoved(contract, avmUtility);
    }

    @Test
    public void resetResetVerify() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        resetStorage(contract, avmUtility);
        resetStorage(contract, avmUtility);
        verifyAllStorageRemoved(contract, avmUtility);
    }

    @Test
    public void resetVerify() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        resetStorage(contract, avmUtility);
        verifyAllStorageRemoved(contract, avmUtility);
    }

    @Test
    public void putZeroVerify() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putStorageLengthZero(contract, avmUtility);
        validateStoragePreviousTxLength(contract, 0, avmUtility);
    }

    @Test
    public void putOneVerify() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putStorageLengthOne(contract, avmUtility);
        validateStoragePreviousTxLength(contract, 1, avmUtility);
    }

    @Test
    public void putResetVerify() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putStorageAddress(contract, avmUtility);
        resetStorage(contract, avmUtility);
        verifyAllStorageRemoved(contract, avmUtility);
    }

    @Test
    public void putAddressVerify() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putStorageAddress(contract, avmUtility);
        validateStoragePreviousTxLength(contract, 32, avmUtility);
    }

    @Test
    public void putAddressResetResetVerify() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putStorageAddress(contract, avmUtility);
        resetStorage(contract, avmUtility);
        resetStorage(contract, avmUtility);
        verifyAllStorageRemoved(contract, avmUtility);
    }

    @Test
    public void putArbitrarySameKeyVerify() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        byte[] b = new byte[]{ 0, 1, 2, 3, 4, 5 };
        setStorageSameKey(contract, b, avmUtility);
        getStorageOneKey(contract, b.length, avmUtility);
    }

    @Test
    public void putZeroSameKeyVerify() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        byte[] b = new byte[0];
        setStorageSameKey(contract, b, avmUtility);
        getStorageOneKey(contract, b.length, avmUtility);
    }

    @Test
    public void putOneSameKeyVerify() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        byte[] b = new byte[] { 0 };
        setStorageSameKey(contract, b, avmUtility);
        getStorageOneKey(contract, b.length, avmUtility);
    }

    @Test
    public void putNullSameKeyVerify() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        setStorageSameKey(contract, null, avmUtility);
        getStorageOneKey(contract, -1, avmUtility);
    }

    @Test
    public void putArbitrarySameKeyVerifyNullSameKeyVerify() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        byte[] b = new byte[]{ 0, 1, 2, 3, 4, 5 };
        setStorageSameKey(contract, b, avmUtility);
        getStorageOneKey(contract, b.length, avmUtility);
        setStorageSameKey(contract, null, avmUtility);
        getStorageOneKey(contract, -1, avmUtility);
    }

    @Test
    public void putZeroSameKeyVerifyNullSameKeyVerify() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        byte[] b = new byte[0];
        setStorageSameKey(contract, b, avmUtility);
        getStorageOneKey(contract, b.length, avmUtility);
        setStorageSameKey(contract, null, avmUtility);
        getStorageOneKey(contract, -1, avmUtility);
    }

    @Test
    public void putOneSameKeyVerifyNullSameKeyVerify() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        byte[] b = new byte[]{ 0 };
        setStorageSameKey(contract, b, avmUtility);
        getStorageOneKey(contract, b.length, avmUtility);
        setStorageSameKey(contract, null, avmUtility);
        getStorageOneKey(contract, -1, avmUtility);
    }

    @Test
    public void removeStorageClinit() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateClinitTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
    }

    @Test
    public void removeStorageReentrant() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        byte[] b = avmUtility.newAvmStreamingEncoder(AvmVersion.VERSION_1).encodeOneString("resetStorage").toBytes();
        reentrantCallAfterPut(contract, b, avmUtility);
    }

    @Test
    public void multipleGetSetVerifiesInSameCall() throws Exception {
        AvmUtility avmUtility = TestHarnessAvmResources.avmUtility();

        RawTransaction deployTransaction = makeAvmCreateTransaction(avmUtility);
        TransactionReceipt receipt = sendTransaction(deployTransaction);
        assertTrue(receipt.transactionWasSuccessful());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        Address contract = receipt.getAddressOfDeployedContract().get();

        putZeroResetVerify(contract, avmUtility);
        putOneResetVerify(contract, avmUtility);
        putAddressResetVerify(contract, avmUtility);
        ResetResetVerify(contract, avmUtility);

    }

    private void putStorageLengthZero(Address contract, AvmUtility avmUtility)
        throws InterruptedException, TimeoutException {
        RawTransaction transaction = makeCallTransaction(contract, "putStorageLengthZero", avmUtility);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void resetStorage(Address contract, AvmUtility avmUtility) throws InterruptedException, TimeoutException {
        RawTransaction transaction = makeCallTransaction(contract, "resetStorage", avmUtility);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void verifyAllStorageRemoved(Address contract, AvmUtility avmUtility)
        throws InterruptedException, TimeoutException {
        RawTransaction transaction = makeCallTransaction(contract, "verifyAllStorageRemoved", avmUtility);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void putStorageLengthOne(Address contract, AvmUtility avmUtility) throws InterruptedException, TimeoutException {
        RawTransaction transaction = makeCallTransaction(contract, "putStorageLengthOne", avmUtility);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void putResetPut(Address contract, AvmUtility avmUtility) throws InterruptedException, TimeoutException {
        RawTransaction transaction = makeCallTransaction(contract, "putResetPut", avmUtility);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void putStorageAddress(Address contract, AvmUtility avmUtility) throws InterruptedException, TimeoutException {
        RawTransaction transaction = makeCallTransaction(contract, "putStorageAddress", avmUtility);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void validateStoragePreviousTxLength(Address contract, int i, AvmUtility avmUtility)
        throws InterruptedException, TimeoutException {
        RawTransaction transaction = makeCallTransaction(contract, "validateStoragePreviousTxLength", i, avmUtility);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void getStorageOneKey(Address contract, int i, AvmUtility avmUtility)
        throws InterruptedException, TimeoutException {
        RawTransaction transaction = makeCallTransaction(contract, "getStorageOneKey", i, avmUtility);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void setStorageSameKey(Address contract, byte[] b, AvmUtility avmUtility)
        throws InterruptedException, TimeoutException {
        RawTransaction transaction = makeCallTransaction(contract, "setStorageSameKey", b, avmUtility);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void reentrantCallAfterPut(Address contract, byte[] b, AvmUtility avmUtility)
        throws InterruptedException, TimeoutException {
        RawTransaction transaction = makeCallTransaction(contract, "reentrantCallAfterPut", b, avmUtility);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void putZeroResetVerify(Address contract, AvmUtility avmUtility) throws InterruptedException, TimeoutException {
        RawTransaction transaction = makeCallTransaction(contract, "putZeroResetVerify", avmUtility);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void putOneResetVerify(Address contract, AvmUtility avmUtility) throws InterruptedException, TimeoutException {
        RawTransaction transaction = makeCallTransaction(contract, "putOneResetVerify", avmUtility);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void putAddressResetVerify(Address contract, AvmUtility avmUtility)
        throws InterruptedException, TimeoutException {
        RawTransaction transaction = makeCallTransaction(contract, "putAddressResetVerify", avmUtility);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private void ResetResetVerify(Address contract, AvmUtility avmUtility) throws InterruptedException, TimeoutException {
        RawTransaction transaction = makeCallTransaction(contract, "ResetResetVerify", avmUtility);
        TransactionReceipt receipt = sendTransaction(transaction);
        assertTrue(receipt.transactionWasSuccessful());
    }

    private TransactionReceipt sendTransaction(RawTransaction transaction)
        throws InterruptedException, TimeoutException {
        // we want to ensure that the transaction gets sealed into a block.
        IEvent transactionIsSealed = prepackagedLogEventsFactory.build().getTransactionSealedEvent(transaction);
        IEvent transactionIsRejected = prepackagedLogEventsFactory.build().getTransactionRejectedEvent(transaction);
        IEvent event = Event.or(transactionIsRejected, transactionIsSealed);
        FutureResult<LogEventResult> future = this.listener.listenForEvent(event, 5, TimeUnit.MINUTES);

        // Send the transaction off.
        RpcResult<ReceiptHash> sendResult = rpc.sendTransaction(transaction);
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

    private TransactionReceipt sendGetStaticTransaction(RawTransaction transaction)
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
        RpcResult<ReceiptHash> sendResult = rpc.sendTransaction(transaction);
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

    private RawTransaction makeCallTransaction(Address contract, String method, byte[] b, AvmUtility avmUtility) {
        byte[] data = avmUtility.newAvmStreamingEncoder(AvmVersion.VERSION_1).encodeOneString(method).encodeOneByteArray(b).toBytes();

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

    private RawTransaction makeCallTransaction(Address contract, String method, int i, AvmUtility avmUtility) {
        byte[] data = avmUtility.newAvmStreamingEncoder(AvmVersion.VERSION_1).encodeOneString(method).encodeOneInteger(i).toBytes();

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

    private RawTransaction makeCallTransaction(Address contract, String method, AvmUtility avmUtility) {
        byte[] data = avmUtility.newAvmStreamingEncoder(AvmVersion.VERSION_1).encodeOneString(method).toBytes();

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

    private RawTransaction makeAvmCreateTransaction(AvmUtility avmUtility) {
        TransactionResult buildResult = RawTransaction.buildAndSignAvmCreateTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getAndIncrementNonce(),
            avmUtility.produceAvmJarBytes(AvmVersion.VERSION_1, AvmContract.HARNESS_REMOVED_STORAGE),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO);

        assertTrue(buildResult.isSuccess());
        return buildResult.getTransaction();
    }

    private RawTransaction makeAvmCreateClinitTransaction(AvmUtility avmUtility) {
        TransactionResult buildResult = RawTransaction.buildAndSignAvmCreateTransaction(
            this.preminedAccount.getPrivateKey(),
            this.preminedAccount.getAndIncrementNonce(),
            avmUtility.produceAvmJarBytes(AvmVersion.VERSION_1, AvmContract.HARNESS_CLINIT),
            ENERGY_LIMIT,
            ENERGY_PRICE,
            BigInteger.ZERO);

        assertTrue(buildResult.isSuccess());
        return buildResult.getTransaction();
    }
}
