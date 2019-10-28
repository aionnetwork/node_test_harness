package org.aion.harness.tests.integ.fvmFork;

import static org.aion.harness.tests.contracts.Assertions.assertRpcSuccess;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.PrivateKey;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.main.LocalNode;
import org.aion.harness.main.Network;
import org.aion.harness.main.NodeConfigurations;
import org.aion.harness.main.NodeConfigurations.DatabaseOption;
import org.aion.harness.main.NodeFactory;
import org.aion.harness.main.NodeFactory.NodeType;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.Event;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.event.JavaPrepackagedLogEvents;
import org.aion.harness.main.event.PrepackagedLogEvents;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.types.TransactionReceipt;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.Result;
import org.aion.harness.result.RpcResult;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests billing around data for the fvm before and after the 0.5.0 fork. Before the fork transactions
 * that data that is larger than their energy limit allows for will fail, after the fork these same
 * transactions will be rejected.
 */
public class FvmCallDataBillingTest {
    private static final String PREMINED_KEY = "4c3c8a7c0292bc55d97c50b4bdabfd47547757d9e5c194e89f66f25855baacd0";
    private static final long MIN_CALL_LIMIT = 21_000;
    private static final long ENERGY_PRICE = 10_000_000_000L;
    private static String workingDir = System.getProperty("user.dir");
    private static String kernelDirectoryPath = workingDir + "/aion";
    private static File kernelDirectory = new File(kernelDirectoryPath);
    private static File handwrittenConfigs = new File(workingDir + "/test_resources/custom");
    private static RPC rpc = RPC.newRpc("127.0.0.1", "8545");
    private static PrepackagedLogEvents prepackagedLogEvents = new JavaPrepackagedLogEvents();

    private LocalNode node;
    private PrivateKey preminedAccount;
    private BigInteger preminedNonce = BigInteger.ZERO;
    private IEvent transactionIsSealed;
    private IEvent transactionIsRejected;

    @Before
    public void setup() throws Exception {
        checkKernelExistsAndOverwriteConfigs();
    }

    @After
    public void tearDown() throws Exception {
        if ((node != null) && (node.isAlive())) {
            node.stop();
        }

        // This sleep is to give the OS enough time to release ports between runs.
        Thread.sleep(TimeUnit.SECONDS.toMillis(10));
    }

    @Test
    public void testPreForkBillingForCall() throws Exception {
        writeNewForkPropertiesFileWith050Fork(99999);
        node = initializeNode();
        startNode(node);
        preminedAccount = initializePreminedAccount();

        // Deploy a contract.
        Address contract = deployFvmContract();
        preminedNonce = preminedNonce.add(BigInteger.ONE);

        // Send the call transaction.
        byte[] data = allNonZeroBytes(50_000);
        SignedTransaction transaction = SignedTransaction.newGeneralTransaction(preminedAccount, preminedNonce, contract, data, MIN_CALL_LIMIT, ENERGY_PRICE, BigInteger.ZERO, null);
        TransactionReceipt receipt = sendTransaction(transaction);
        Assert.assertFalse(receipt.transactionWasSuccessful());
        Assert.assertTrue(this.transactionIsSealed.hasBeenObserved());
        Assert.assertFalse(this.transactionIsRejected.hasBeenObserved());
    }

    @Test
    public void testPostForkBillingForCall() throws Exception {
        writeNewForkPropertiesFileWith050Fork(2);
        node = initializeNode();
        startNode(node);
        preminedAccount = initializePreminedAccount();

        // Deploy a contract.
        Address contract = deployFvmContract();
        preminedNonce = preminedNonce.add(BigInteger.ONE);

        boolean isPostFork = false;
        while (!isPostFork) {
            SignedTransaction transaction = SignedTransaction.newGeneralTransaction(preminedAccount, preminedNonce, PrivateKey.random().getAddress(), new byte[0], 2_000_000, ENERGY_PRICE, BigInteger.ONE, null);
            TransactionReceipt receipt = sendTransaction(transaction);
            Assert.assertNotNull(receipt);
            preminedNonce = preminedNonce.add(BigInteger.ONE);

            isPostFork = receipt.getBlockNumber().compareTo(BigInteger.TWO) >= 0;
        }
        System.out.println("0.5.0 fork point has passed!");

        // Send the call transaction.
        byte[] data = allNonZeroBytes(50_000);
        SignedTransaction transaction = SignedTransaction.newGeneralTransaction(preminedAccount, preminedNonce, contract, data, MIN_CALL_LIMIT, ENERGY_PRICE, BigInteger.ZERO, null);
        TransactionReceipt receipt = sendTransaction(transaction);
        Assert.assertNull(receipt);
        Assert.assertFalse(this.transactionIsSealed.hasBeenObserved());
    }

    private Address deployFvmContract() throws Exception {
        String contractCodeAsString = "605060405234156100105760006000fd5b5b6101026000600050819090600019169055505b610029565b60e0806100376000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680636d4ce63c14603b578063ac6effc214606a576035565b60006000fd5b341560465760006000fd5b604c608f565b60405180826000191660001916815260100191505060405180910390f35b341560755760006000fd5b608d60048080356000191690601001909190505060a0565b005b60006000600050549050609d565b90565b806000600050819090600019169055505b505600a165627a7a72305820d6585e5cd5b612562558cd595d992e3cde87a53ed8765ab62c9662cca20d914a0029";
        byte[] contractCode = Hex.decodeHex(contractCodeAsString);

        SignedTransaction transaction = SignedTransaction.newGeneralTransaction(preminedAccount, preminedNonce, null, contractCode, 2_000_000L, ENERGY_PRICE, BigInteger.ZERO, null);
        TransactionReceipt receipt = sendTransaction(transaction);
        Assert.assertTrue(receipt.transactionWasSuccessful());
        Assert.assertTrue(this.transactionIsSealed.hasBeenObserved());
        Assert.assertFalse(this.transactionIsRejected.hasBeenObserved());
        Assert.assertTrue(receipt.getAddressOfDeployedContract().isPresent());

        return receipt.getAddressOfDeployedContract().get();
    }

    private static byte[] allNonZeroBytes(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) 0xff;
        }
        return bytes;
    }

    private TransactionReceipt sendTransaction(SignedTransaction transaction) throws InterruptedException, TimeoutException {
        // we want to ensure that the transaction gets sealed into a block.
        this.transactionIsSealed = prepackagedLogEvents.getTransactionSealedEvent(transaction);
        this.transactionIsRejected = prepackagedLogEvents.getTransactionRejectedEvent(transaction);
        IEvent transactionProcessed = Event.or(this.transactionIsSealed, this.transactionIsRejected);

        NodeListener listener = NodeListener.listenTo(node);
        FutureResult<LogEventResult> future = listener.listenForEvent(transactionProcessed, 5, TimeUnit.MINUTES);

        // Send the transaction off.
        System.out.println("Sending the transaction...");
        RpcResult<ReceiptHash> sendResult = rpc.sendSignedTransaction(transaction);

        if (!sendResult.isSuccess()) {
            return null;
        }

        // Wait on the future to complete and ensure we saw the transaction get sealed.
        System.out.println("Waiting for the transaction to process...");
        LogEventResult listenResult = future.get(5, TimeUnit.MINUTES);
        assertTrue(listenResult.eventWasObserved());
        System.out.println("Transaction was processed!");

        ReceiptHash hash = sendResult.getResult();

        RpcResult<TransactionReceipt> receiptResult = rpc.getTransactionReceipt(hash);
        assertRpcSuccess(receiptResult);

        return receiptResult.getResult();
    }

    //<-------------------------------------NODE SETUP HELPERS------------------------------------->

    private static void startNode(LocalNode node) throws IOException, InterruptedException {
        Result result = node.start();
        if (!result.isSuccess()) {
            throw new IllegalStateException("Failed to start node: " + result.getError());
        }
    }

    private static LocalNode initializeNode() throws IOException, InterruptedException {
        NodeConfigurations configurations = NodeConfigurations.alwaysUseBuiltKernel(Network.CUSTOM, kernelDirectoryPath, DatabaseOption.DO_NOT_PRESERVE_DATABASE);
        LocalNode node = NodeFactory.getNewLocalNodeInstance(NodeType.JAVA_NODE);
        node.configure(configurations);
        Result result = node.initialize();
        if (!result.isSuccess()) {
            throw new IllegalStateException("Failed to initialize node: " + result.getError());
        }
        return node;
    }

    private static void checkKernelExistsAndOverwriteConfigs() throws IOException {
        if (!kernelDirectory.exists() || !kernelDirectory.isDirectory()) {
            throw new IllegalStateException("Expected to find a kernel at: " + kernelDirectoryPath);
        }
        overwriteConfigAndGenesis();
    }

    private static void overwriteConfigAndGenesis() throws IOException {
        // Copy all our handwritten stuff over but delete the fork.properties file so tests can add their own.
        FileUtils.copyDirectory(handwrittenConfigs, new File(kernelDirectoryPath + "/custom"));
        deleteForkPropertiesFile();
    }

    private static PrivateKey initializePreminedAccount() throws DecoderException, InvalidKeySpecException {
        return PrivateKey.fromBytes(Hex.decodeHex(PREMINED_KEY));
    }

    private static void deleteForkPropertiesFile() throws IOException {
        File forkProperties = new File(kernelDirectoryPath + "/custom/config/fork.properties");
        if (forkProperties.exists()) {
            forkProperties.delete();
        }
    }

    private static void writeNewForkPropertiesFileWith050Fork(int number) throws IOException {
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(kernelDirectoryPath + "/custom/config/fork.properties"), "utf-8"))) {
            writer.write("fork0.3.2=0\nfork0.4.0=0\nfork0.5.0=" + number);
        }
    }
}
