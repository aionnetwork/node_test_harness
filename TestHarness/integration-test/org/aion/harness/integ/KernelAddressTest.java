package org.aion.harness.integ;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.concurrent.TimeUnit;
import org.aion.harness.integ.resources.TestHelper;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.PrivateKey;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.kernel.utils.CryptoUtils;
import org.aion.harness.main.LocalNode;
import org.aion.harness.main.Network;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.JavaPrepackagedLogEvents;
import org.aion.harness.result.FutureResult;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.misc.Assumptions;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.Result;
import org.aion.harness.result.RpcResult;
import org.aion.harness.util.NodeFileManager;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.security.spec.InvalidKeySpecException;

import static org.junit.Assert.*;

public class KernelAddressTest {
    private static PrivateKey preminedPrivateKey;
    private static Address destination;

    private LocalNode node;
    private RPC rpc;

    @Before
    public void setup() throws IOException, InterruptedException, DecoderException, InvalidKeySpecException {
        preminedPrivateKey = PrivateKey.fromBytes(Hex.decodeHex(Assumptions.PREMINED_PRIVATE_KEY));
        destination = new Address(Hex.decodeHex("a0e9f9832d581246a9665f64599f405e8927993c6bef4be2776d91a66b466d30"));

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
    public void testDeriveAddress() throws DecoderException, InvalidKeySpecException {
        String preminedPrivateKey = "223f19377d95582055bd8972cf3ffd635d2712a7171e4888091a066b9f4f63d5";
        String preminedAddress = "a0d6dec327f522f9c8d342921148a6c42f40a3ce45c1f56baa7bfa752200d9e5";

        byte[] computedAddress = CryptoUtils.deriveAddress(Hex.decodeHex(preminedPrivateKey));
        assertArrayEquals(Hex.decodeHex(preminedAddress), computedAddress);
    }


    @Test
    public void testCorrectness() throws Exception {
        NodeListener nodeListener = NodeListener.listenTo(this.node);
        PrivateKey senderPrivateKey = PrivateKey.random();
        System.out.println("private key = " + senderPrivateKey);

        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        // Transfer funds to the new private key so it can pay for transaction.
        transferFunds(nodeListener, senderPrivateKey);

        // send transaction with new address private key
        SignedTransaction transaction = constructTransaction(
            senderPrivateKey,
            destination,
            BigInteger.ZERO,
            BigInteger.ZERO);

        FutureResult<LogEventResult> futureResult = nodeListener.listenForEvent(
            new JavaPrepackagedLogEvents().getTransactionProcessedEvent(transaction),
            2,
            TimeUnit.MINUTES);

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendSignedTransaction(transaction);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        LogEventResult logEventResult = futureResult.get();
        assertTrue(logEventResult.eventWasObserved());

        // use the receipt to determine the real address
        RpcResult<TransactionReceipt> rpcResult2 = this.rpc.getTransactionReceipt(rpcResult.getResult());
        assertTrue(rpcResult2.isSuccess());
        TransactionReceipt transactionReceipt = rpcResult2.getResult();

        assertEquals(senderPrivateKey.getAddress(), transactionReceipt.getTransactionSender());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    private void transferFunds(NodeListener nodeListener, PrivateKey senderPrivateKey) throws Exception {
        SignedTransaction transaction = constructTransaction(
            preminedPrivateKey,
            senderPrivateKey.getAddress(),
            BigInteger.TEN.pow(20),
            BigInteger.ZERO);

        FutureResult<LogEventResult> futureResult = nodeListener.listenForEvent(
            new JavaPrepackagedLogEvents().getTransactionProcessedEvent(transaction),
            2,
            TimeUnit.MINUTES);

        RpcResult<ReceiptHash> rpcResult = this.rpc.sendSignedTransaction(transaction);
        System.out.println("Rpc result = " + rpcResult);
        assertTrue(rpcResult.isSuccess());

        LogEventResult logEventResult = futureResult.get();
        assertTrue(logEventResult.eventWasObserved());
    }

    private SignedTransaction constructTransaction(PrivateKey senderPrivateKey, Address destination, BigInteger value, BigInteger nonce) throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        return SignedTransaction
            .newGeneralTransaction(senderPrivateKey, nonce, destination, new byte[0], 2_000_000, 10_000_000_000L, value,
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
