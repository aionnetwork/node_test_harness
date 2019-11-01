package org.aion.harness.integ;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.Optional;
import org.aion.harness.integ.resources.TestHelper;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.Kernel;
import org.aion.harness.kernel.PrivateKey;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.kernel.UnsignedTransaction;
import org.aion.harness.main.LocalNode;
import org.aion.harness.main.event.JavaPrepackagedLogEvents;
import org.aion.harness.main.impl.JavaNode;
import org.aion.harness.main.types.Block;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.RPC;
import org.aion.harness.result.FutureResult;
import org.aion.harness.main.Network;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.misc.Assumptions;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.RpcResult;
import org.aion.harness.result.Result;
import org.aion.harness.util.NodeFileManager;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.junit.*;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class RpcTest {
    private static Address destination;
    private static PrivateKey preminedPrivateKey;
    private static long energyLimit = 2_000_000;
    private static long energyPrice = 10_000_000_000L;

    private LocalNode node;
    private RPC rpc;

    @Before
    public void setup() throws IOException, InterruptedException, DecoderException, InvalidKeySpecException {
        destination = new Address(Hex.decodeHex("a0e9f9832d581246a9665f64599f405e8927993c6bef4be2776d91a66b466d30"));
        preminedPrivateKey = PrivateKey.fromBytes(Hex.decodeHex(Assumptions.PREMINED_PRIVATE_KEY));

        this.node = TestHelper.configureDefaultLocalNodeForNetwork(Network.CUSTOM);

        this.rpc = RPC.newRpc("127.0.0.1", "8545");

        assertTrue(this.node.initialize().isSuccess());
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        shutdownNodeIfRunning();
        this.node = null;
        this.rpc = null;
    }

    @AfterClass
    public static void tearDownAfterAllTests() throws IOException {
        deleteLogs();
    }

    @Test
    public void testUnlockAccount() throws Exception {
        String password = "test";

        // First we create the new keystore account.
        JavaNode javaNode = (JavaNode) this.node;
        Kernel kernel = javaNode.getKernel();
        Address account = kernel.createNewAccountInKeystore(password);

        // Now start the node up.
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        // Try to unlock the account.
        RpcResult<Boolean> unlockResult = this.rpc.unlockKeystoreAccount(account, password, 10, TimeUnit.SECONDS);
        System.out.println("Unlock result = " + unlockResult);
        Assert.assertTrue(unlockResult.isSuccess());

        // Actually confirm that the kernel said the account was unlocked.
        Assert.assertTrue(unlockResult.getResult());

        // Shut the node down.
        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testUnlockAccountBadPassword() throws Exception {
        String password = "test";

        // First we create the new keystore account.
        JavaNode javaNode = (JavaNode) this.node;
        Kernel kernel = javaNode.getKernel();
        Address account = kernel.createNewAccountInKeystore(password);

        // Now start the node up.
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        // Try to unlock the account.
        RpcResult<Boolean> unlockResult = this.rpc.unlockKeystoreAccount(account, "notthepassword", 10, TimeUnit.SECONDS);
        System.out.println("Unlock result = " + unlockResult);
        Assert.assertTrue(unlockResult.isSuccess());

        // Actually confirm that the kernel said the account was not unlocked.
        Assert.assertFalse(unlockResult.getResult());

        // Shut the node down and clean up the keystore.
        kernel.clearKeystore();

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testSendUnsignedTransaction() throws Exception {
        String password = "test";

        // First we create the new keystore account.
        JavaNode javaNode = (JavaNode) this.node;
        Kernel kernel = javaNode.getKernel();
        Address account = kernel.createNewAccountInKeystore(password);

        // Now start the node up.
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        // Try to unlock the account.
        RpcResult<Boolean> unlockResult = this.rpc.unlockKeystoreAccount(account, password, 10, TimeUnit.MINUTES);
        System.out.println("Unlock result = " + unlockResult);
        Assert.assertTrue(unlockResult.isSuccess());

        // Actually confirm that the kernel said the account was not unlocked.
        Assert.assertTrue(unlockResult.getResult());

        // Fund the account with money from the premined account and then send a transaction.
        fundAccount(account);

        UnsignedTransaction transaction = UnsignedTransaction.newNonCreateTransaction(account, preminedPrivateKey.getAddress(), BigInteger.ZERO, BigInteger.ONE, new byte[0], 50_000L, 10_000_000_000L);
        RpcResult<ReceiptHash> sendResult = this.rpc.sendUnsignedTransaction(transaction);
        System.out.println("Send result = " + sendResult);
        Assert.assertTrue(sendResult.isSuccess());

        // Shut the node down and clean up the keystore.
        kernel.clearKeystore();

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testSendUnsignedTransactionAccountNotUnlocked() throws Exception {
        Address account = PrivateKey.random().getAddress();

        // Now start the node up.
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        // Fund the account with money from the premined account and then send a transaction.
        fundAccount(account);

        UnsignedTransaction transaction = UnsignedTransaction.newNonCreateTransaction(account, preminedPrivateKey.getAddress(), BigInteger.ZERO, BigInteger.ONE, new byte[0], 50_000L, 10_000_000_000L);
        RpcResult<ReceiptHash> sendResult = this.rpc.sendUnsignedTransaction(transaction);

        // We should fail to send.
        System.out.println("Send result = " + sendResult);
        Assert.assertFalse(sendResult.isSuccess());

        // Shut the node down.
        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testSendValueViaRPC() throws Exception {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        SignedTransaction transaction = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.ZERO);

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendSignedTransaction(transaction);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testGetBalanceViaRPC() throws Exception {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        RpcResult<BigInteger> rpcResult = this.rpc.getBalance(destination);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testSendValueWithIncorrectNonceViaRPC() throws Exception {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        SignedTransaction transaction = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.valueOf(100));

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendSignedTransaction(transaction);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testSendNegativeValueViaRPC() throws Exception {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        SignedTransaction transaction = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.valueOf(-1),
            BigInteger.ZERO);

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendSignedTransaction(transaction);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testSendZeroValueViaRPC() throws Exception {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        SignedTransaction transaction = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ZERO,
            BigInteger.ZERO);

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendSignedTransaction(transaction);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testSendMultipleValueTransfersViaRPC() throws Exception {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        SignedTransaction transaction = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.ZERO);

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendSignedTransaction(transaction);
        assertTrue(rpcResult.isSuccess());

        transaction = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.ONE);

        rpcResult = this.rpc.sendSignedTransaction(transaction);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testSendTransactionWhenNoNodeIsAlive() throws Exception {
        assertFalse(this.node.isAlive());
        SignedTransaction transaction = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ZERO,
            BigInteger.ZERO);

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendSignedTransaction(transaction);
        System.out.println("Rpc result = " + rpcResult);
        assertFalse(rpcResult.isSuccess());
    }

    @Test
    public void testSendValueToInvalidAddress() throws Exception {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        SignedTransaction transaction = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.ZERO);

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendSignedTransaction(transaction);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testSendValueWithInsufficientBalance() throws Exception {
        byte[] badKey = Hex.decodeHex("223f19370d95582055bd8072cf3ffd635d2712a7171e4888091a060b9f4f63d5");
        PrivateKey badPrivateKey = PrivateKey.fromBytes(badKey);

        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        SignedTransaction transaction = constructTransaction(
            badPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.ZERO);

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendSignedTransaction(transaction);
        System.out.println("Rpc result = " + rpcResult);
        assertFalse(rpcResult.isSuccess());
        assertTrue(rpcResult.getError().contains("Transaction dropped"));

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testBalanceTransferAndCheck() throws Exception {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        BigInteger transferValue = BigInteger.valueOf(50);

        // check balance before
        RpcResult<BigInteger> rpcResult = this.rpc.getBalance(destination);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());
        assertEquals(BigInteger.ZERO, rpcResult.getResult());

        // transfer and wait
        doBalanceTransfer(transferValue);

        // check balance after
        rpcResult = this.rpc.getBalance(destination);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());
        assertEquals(transferValue, rpcResult.getResult());
    }

    @Test
    public void testBalanceTransferZeroBalanceAndCheck() throws Exception {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        BigInteger transferValue = BigInteger.ZERO;

        // check balance before
        RpcResult<BigInteger> rpcResult = this.rpc.getBalance(destination);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());
        assertEquals(BigInteger.ZERO, rpcResult.getResult());

        // transfer and wait
        doBalanceTransfer(transferValue);

        // check balance after
        rpcResult = this.rpc.getBalance(destination);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());
        assertEquals(BigInteger.ZERO, rpcResult.getResult());
    }

    @Test
    public void testBalanceTransferNegativeBalanceAndCheck() throws Exception {
        // When someone tries to transfer a negative value(BigInteger), the positive representation of
        // that BigInteger will be the transfer value. This should be something handled by the server.
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        BigInteger negativeTransferValue = BigInteger.valueOf(-10);
        BigInteger positiveRepresentation = new BigInteger(1, negativeTransferValue.toByteArray());

        // check balance before
        RpcResult<BigInteger> rpcResult = this.rpc.getBalance(destination);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());
        assertEquals(BigInteger.ZERO, rpcResult.getResult());

        // transfer and wait
        doBalanceTransfer(negativeTransferValue);

        // check balance after - for positive representation
        rpcResult = this.rpc.getBalance(destination);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());
        assertEquals(positiveRepresentation, rpcResult.getResult());
    }

    @Test
    public void testGetNonce() throws Exception {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        Address preminedAddress = preminedPrivateKey.getAddress();

        // check nonce before
        RpcResult<BigInteger> rpcResult = this.rpc.getNonce(preminedAddress);
        System.out.println("Rpc result = " + rpcResult);

        BigInteger nonceBefore = rpcResult.getResult();
        System.out.println("nonce is: " + nonceBefore);

        // do a transfer and wait
        doBalanceTransfer(BigInteger.ONE);

        // check nonce after
        rpcResult = this.rpc.getNonce(preminedAddress);
        System.out.println("Rpc result = " + rpcResult);

        BigInteger nonceAfter = rpcResult.getResult();
        System.out.println("nonce is: " + nonceAfter);

        assertEquals(nonceBefore.add(BigInteger.ONE), nonceAfter);
    }

    @Test
    public void testGetTransactionReceipt() throws Exception {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        SignedTransaction transaction = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.ZERO);

        NodeListener listener = NodeListener.listenTo(this.node.getID());

        FutureResult<LogEventResult> futureResult = listener.listenForEvent(
            new JavaPrepackagedLogEvents().getTransactionProcessedEvent(transaction),
            2,
            TimeUnit.MINUTES);

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendSignedTransaction(transaction);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        LogEventResult waitResult = futureResult.get();
        System.out.println("Listener result = " + waitResult);
        assertTrue(waitResult.eventWasObserved());

        RpcResult<TransactionReceipt> receiptResult = this.rpc.getTransactionReceipt(rpcResult.getResult());
        System.out.println("Receipt result = " + receiptResult);
        assertTrue(receiptResult.isSuccess());

        // Assert the fields in the receipt are as expected.
        TransactionReceipt receipt = receiptResult.getResult();

        assertEquals(energyLimit, receipt.getTransactionEnergyLimit());
        assertEquals(energyPrice, receipt.getTransactionEnergyPrice());
        assertEquals(21_000L, receipt.getTransactionEnergyConsumed());
        assertEquals(21_000L, receipt.getCumulativeEnergyConsumed());
        assertEquals(0, receipt.getTransactionIndex());
        assertArrayEquals(transaction.getTransactionHash(), receipt.getTransactionHash());
        assertEquals(BigInteger.ONE, receipt.getBlockNumber());
        assertFalse(receipt.getAddressOfDeployedContract().isPresent());
        assertEquals(preminedPrivateKey.getAddress(), receipt.getTransactionSender());

        Optional<Address> transactionDestination = receipt.getTransactionDestination();
        assertTrue(transactionDestination.isPresent());
        assertEquals(destination, transactionDestination.get());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testGetTransactionReceiptFromContractDeploy() throws Exception {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        SignedTransaction transaction = constructTransaction(
            preminedPrivateKey,
            null,
            BigInteger.ONE,
            BigInteger.ZERO);

        NodeListener listener = NodeListener.listenTo(this.node.getID());

        FutureResult<LogEventResult> futureResult = listener.listenForEvent(
            new JavaPrepackagedLogEvents().getTransactionProcessedEvent(transaction),
            2,
            TimeUnit.MINUTES);

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendSignedTransaction(transaction);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        LogEventResult waitResult = futureResult.get();
        System.out.println("Listener result = " + waitResult);
        assertTrue(waitResult.eventWasObserved());

        RpcResult<TransactionReceipt> receiptResult = this.rpc.getTransactionReceipt(rpcResult.getResult());
        System.out.println("Receipt result = " + receiptResult);
        assertTrue(receiptResult.isSuccess());

        // Assert the fields in the receipt are as expected.
        TransactionReceipt receipt = receiptResult.getResult();

        assertEquals(energyLimit, receipt.getTransactionEnergyLimit());
        assertEquals(energyPrice, receipt.getTransactionEnergyPrice());
        assertEquals(0, receipt.getTransactionIndex());
        assertArrayEquals(transaction.getTransactionHash(), receipt.getTransactionHash());
        assertEquals(BigInteger.ONE, receipt.getBlockNumber());
        assertTrue(receipt.getAddressOfDeployedContract().isPresent());
        assertEquals(preminedPrivateKey.getAddress(), receipt.getTransactionSender());
        assertFalse(receipt.getTransactionDestination().isPresent());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testGetTransactionReceiptFromNonExistentReceiptHash() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        ReceiptHash receiptHash = new ReceiptHash(new byte[32]);

        RpcResult<TransactionReceipt> receiptResult = this.rpc.getTransactionReceipt(receiptHash);
        System.out.println("Receipt result = " + receiptResult);
        assertFalse(receiptResult.isSuccess());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    /**
     * This test does not assert anything about the syncing process because there's not really
     * anything meaningful to latch on to. It is here so that we can visually monitor this output.
     */
    @Test
    public void testSyncingToNetwork() throws IOException, InterruptedException {
        this.node = TestHelper.configureDefaultLocalNodeForNetwork(Network.MAINNET);
        assertTrue(this.node.initialize().isSuccess());

        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        // Wait a bit so that we actually start syncing, otherwise RPC will tell us we are done.
        Thread.sleep(TimeUnit.SECONDS.toMillis(10));

        Result syncResult = this.rpc.waitForSyncToComplete(40, TimeUnit.SECONDS);
        System.out.println("Sync result: " + syncResult);

        // Since we are syncing fresh from mainnet we really should timeout after 40 seconds!
        assertFalse(syncResult.isSuccess());
        assertEquals("Timed out waiting for sync to finish.", syncResult.getError());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testGetBlockByNumber() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        RpcResult<Block> blockResult = this.rpc.getBlockByNumber(BigInteger.ZERO);
        assertTrue(blockResult.isSuccess());
        assertNotNull(blockResult.getResult());

        assertTrue(this.node.stop().isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testGetBlockByNumberWhenBlockDoesNotExist() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        RpcResult<Block> blockResult = this.rpc.getBlockByNumber(BigInteger.valueOf(2349865));
        assertFalse(blockResult.isSuccess());
        assertTrue(blockResult.getError().contains("No block exists"));

        assertTrue(this.node.stop().isSuccess());
        assertFalse(this.node.isAlive());
    }

    private void fundAccount(Address address) throws InterruptedException, InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        SignedTransaction transaction = SignedTransaction.newGeneralTransaction(preminedPrivateKey, BigInteger.ZERO, address, new byte[0], 50_000L, 10_000_000_000L, BigInteger.valueOf(10_000_000_000_000_000L),
            null);
        RpcResult<ReceiptHash> result = this.rpc.sendSignedTransaction(transaction);
        Assert.assertTrue(result.isSuccess());
    }

    private void doBalanceTransfer(BigInteger transferValue) throws Exception {
        SignedTransaction transaction = constructTransaction(
            preminedPrivateKey,
            destination,
            transferValue,
            BigInteger.ZERO);

        FutureResult<LogEventResult> futureResult = NodeListener.listenTo(this.node.getID()).listenForEvent(
            new JavaPrepackagedLogEvents().getTransactionProcessedEvent(transaction),
            1,
            TimeUnit.MINUTES);

        RpcResult<ReceiptHash> result = this.rpc.sendSignedTransaction(transaction);
        System.out.println("Rpc result = " + result);
        assertTrue(result.isSuccess());

        LogEventResult eventResult = futureResult.get();
        assertTrue(eventResult.eventWasObserved());
    }

    private SignedTransaction constructTransaction(PrivateKey senderPrivateKey, Address destination, BigInteger value, BigInteger nonce) throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        return SignedTransaction
            .newGeneralTransaction(senderPrivateKey, nonce, destination, new byte[0], energyLimit, energyPrice, value,
                null);
    }

    private static void deleteLogs() throws IOException {
        FileUtils.deleteDirectory(NodeFileManager.getLogsDirectory());
    }

    private void shutdownNodeIfRunning() throws IOException, InterruptedException {
        if ((this.node != null) && (this.node.isAlive())) {
            Result result = this.node.stop();
            System.out.println("shutdownNodeIfRunning Stop result = " + result);

            assertTrue(result.isSuccess());
            assertFalse(this.node.isAlive());
        }
    }
}
