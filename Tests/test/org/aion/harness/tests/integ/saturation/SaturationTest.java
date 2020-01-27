package org.aion.harness.tests.integ.saturation;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aion.equihash.EquihashMiner;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.BulkRawTransactionBuilder;
import org.aion.harness.kernel.BulkRawTransactionBuilder.TransactionType;
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
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.util.TestHarnessHelper;
import org.aion.harness.result.BulkResult;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.Result;
import org.aion.harness.result.RpcResult;
import org.aion.harness.tests.integ.runner.internal.StakingBlockSigner;
import org.aion.harness.tests.integ.runner.internal.UnityBootstrap;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class SaturationTest {
    private static String kernelDirectoryPath = System.getProperty("user.dir") + "/oan";
    private static File kernelDirectory = new File(kernelDirectoryPath);
    private static File handwrittenConfigs = new File(System.getProperty("user.dir") + "/test_resources/custom/config");
    private static LocalNode node;
    private static PrivateKey preminedAccount;
    private static BigInteger preminedNonce = BigInteger.ZERO;
    private static JavaPrepackagedLogEvents prepackagedLogEvents = new JavaPrepackagedLogEvents();
    private static RPC rpc;
    private static EquihashMiner miner;
    private static StakingBlockSigner signer;

    private static final String PREMINED_KEY = "4c3c8a7c0292bc55d97c50b4bdabfd47547757d9e5c194e89f66f25855baacd0";
    private static final BigInteger INITIAL_SENDER_BALANCE = BigInteger.valueOf(1_000_000_000_000_000_000L).multiply(BigInteger.valueOf(100));
    public static final BigInteger TRANSFER_AMOUNT = BigInteger.valueOf(100);
    public static final long ENERGY_LIMIT = 50_000L;
    public static final long ENERGY_PRICE = 10_000_000_000L;

    // ~~~~~~~ The control variables ~~~~~~~~~
    public static final long THREAD_TIMEOUT_IN_NANOS = TimeUnit.HOURS.toNanos(5);   // Max timeout for waiting for transactions to process.
    public static final long THREAD_DELAY_IN_MILLIS = TimeUnit.MINUTES.toMillis(2); // Time to sleep between checking if all transactions processed.
    private static final int NUM_SENDERS = 1000;        // Number of threads sending transactions.
    public static final int NUM_TRANSACTIONS = 120;     // Number of transactions each thread sends.

    @BeforeClass
    public static void setupNode() throws Exception {
        if (System.getProperty("rpcPort") == null) {
            Random random = new Random();
            System.setProperty("rpcPort", Integer.toString(random.nextInt(1000) + 9000));
        }

        checkKernelExistsAndOverwriteConfigs();
        setRpcPort(System.getProperty("rpcPort"));
        node = initializeNode();
        startNode(node);
        rpc = RPC.newDefaultRpc();
        miner = EquihashMiner.defaultMiner();
        miner.startMining();
        String port = System.getProperty("rpcPort");
        UnityBootstrap.bootstrap(System.getProperty("rpcPort"));
        signer = StakingBlockSigner.defaultStakingBlockSigner();
        signer.start();
        preminedAccount = initializePreminedAccount();
    }

    @AfterClass
    public static void tearDownNode() throws Exception {
        signer.stop();
        miner.stopMining();
        if ((node != null) && (node.isAlive())) {
            node.stop();
        }
    }

    @Test
    public void saturationTest() throws Exception {
        // Some basic assertions to catch any silly static errors.
        assertPreminedAccountHasSufficientBalanceToTransfer();
        assertSendersAllHaveSufficientBalanceToSendTransactions();

        // Set up the sender accounts, giving them each enough balance for their transactions.
        System.out.println("Initializing all of the sender accounts with " + INITIAL_SENDER_BALANCE + " balance each ...");
        List<PrivateKey> senderKeys = initializeAllSenderAccounts();
        assertAllSendersHaveExpectedBalance(senderKeys);
        System.out.println("All sender accounts initialized!");

        // Start the saturation threads and wait for them to complete.
        System.out.println("Initializing and starting all sender threads ...");
        List<Saturator> saturators = createAllSaturators(senderKeys);
        List<FutureTask<SaturationReport>> tasks = createAllFutureTasks(saturators);
        List<Thread> threads = createAllThreads(tasks);
        startAllThreads(threads);
        System.out.println("All sender threads initialized and started!");

        // Collect the reports from the various threads and clean up.
        System.out.println("Waiting on all the thread reports ...");
        boolean encounteredError = false;
        for (FutureTask<SaturationReport> task : tasks) {
            SaturationReport report = task.get();
            if (!report.saturationWasSuccessful) {
                encounteredError = true;
                System.out.println(report.threadName + " encountered an error: " + report.causeOfError);
            }
        }
        System.out.println("All thread reports collected!");

        waitForAllThreadsToComplete(threads);

        // We want to fail out if we did encounter an error, just so it's obvious.
        Assert.assertFalse(encounteredError);
    }

    private static List<Thread> createAllThreads(List<FutureTask<SaturationReport>> tasks) {
        List<Thread> threads = new ArrayList<>();
        for (FutureTask<SaturationReport> task : tasks) {
            threads.add(new Thread(task));
        }
        return threads;
    }

    private static List<Saturator> createAllSaturators(List<PrivateKey> senderKeys) {
        CyclicBarrier barrier = new CyclicBarrier(NUM_SENDERS);
        List<Saturator> threads = new ArrayList<>();
        for (int i = 0; i < NUM_SENDERS; i++) {
            threads.add(new Saturator(i, barrier, senderKeys.get(i)));
        }
        return threads;
    }

    private static List<FutureTask<SaturationReport>> createAllFutureTasks(List<Saturator> saturators) {
        List<FutureTask<SaturationReport>> threads = new ArrayList<>();
        for (Saturator saturator : saturators) {
            threads.add(new FutureTask<>(saturator));
        }
        return threads;
    }

    private static void startAllThreads(List<Thread> threads) {
        for (Thread thread : threads) {
            thread.start();
        }
    }

    private static void waitForAllThreadsToComplete(List<Thread> threads) throws InterruptedException {
        for (Thread thread : threads) {
            thread.join();
        }
    }

    /**
     * Creates NUM_SENDERS new random accounts and gives each of them INITIAL_SENDER_BALANCE amount
     * of funds. This method fails if anything goes wrong in this initialization process. If it
     * returns then these addresses are ready to use.
     *
     * This method returns the private keys of the accounts, from which their addresses can be
     * discovered.
     */
    private static List<PrivateKey> initializeAllSenderAccounts() throws InvalidKeySpecException, InterruptedException, TimeoutException {
        List<PrivateKey> senderKeys = newRandomKeys(NUM_SENDERS);
        fundAllSenderAccounts(toAddresses(senderKeys));
        return senderKeys;
    }

    private static List<PrivateKey> newRandomKeys(int num) throws InvalidKeySpecException {
        List<PrivateKey> keys = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            keys.add(PrivateKey.random());
        }
        return keys;
    }

    /**
     * Transfers INITIAL_SENDER_BALANCE amount of aion to each of the listed senders.
     */
    private static void fundAllSenderAccounts(List<Address> senders) throws InterruptedException, TimeoutException {
        BulkResult<SignedTransaction> builderResult = new BulkRawTransactionBuilder(NUM_SENDERS)
            .useSameSender(preminedAccount, preminedNonce)
            .useMultipleDestinations(senders)
            .useSameTransferValue(INITIAL_SENDER_BALANCE)
            .useSameTransactionData(new byte[0])
            .useSameEnergyLimit(ENERGY_LIMIT)
            .useSameEnergyPrice(ENERGY_PRICE)
            .useSameTransactionType(TransactionType.FVM)
            .build();
        Assert.assertTrue(builderResult.isSuccess());
        List<SignedTransaction> transactions = builderResult.getResults();

        // Start listening for the transactions to be processed.
        NodeListener listener = NodeListener.listenTo(node.getID());
        List<ProcessedTransactionEventHolder> transactionProcessedEvents = constructTransactionProcessedEvents(transactions);
        List<IEvent> processedEvents = extractOnlyTransactionIsProcessedEvent(transactionProcessedEvents);
        List<FutureResult<LogEventResult>> futures = listener.listenForEvents(processedEvents, 5, TimeUnit.MINUTES);

        // Send the transactions.
        List<RpcResult<ReceiptHash>> bulkSendResults = rpc.sendSignedTransactions(transactions);
        BulkResult<ReceiptHash> bulkResults = TestHarnessHelper.extractRpcResults(bulkSendResults);
        Assert.assertTrue(bulkResults.isSuccess());

        // Wait for the transactions to finish processing.
        TestHarnessHelper.waitOnFutures(futures);

        // Verify all of the transactions were sealed into blocks.
        for (ProcessedTransactionEventHolder transactionProcessedEvent : transactionProcessedEvents) {
            Assert.assertTrue(transactionProcessedEvent.transactionIsSealed.hasBeenObserved());
            Assert.assertFalse(transactionProcessedEvent.transactionIsRejected.hasBeenObserved());
        }

        // Increment the premined nonce.
        preminedNonce = preminedNonce.add(BigInteger.valueOf(NUM_SENDERS));
    }

    private static void assertPreminedAccountHasSufficientBalanceToTransfer() throws InterruptedException {
        BigInteger energyCost = BigInteger.valueOf(ENERGY_LIMIT).multiply(BigInteger.valueOf(ENERGY_PRICE));
        BigInteger transferTransactionCost = energyCost.add(INITIAL_SENDER_BALANCE);
        BigInteger totalCost = transferTransactionCost.multiply(BigInteger.valueOf(NUM_SENDERS));
        Assert.assertTrue(rpc.getBalance(preminedAccount.getAddress()).getResult().compareTo(totalCost) >= 0);
    }

    private static void assertSendersAllHaveSufficientBalanceToSendTransactions() {
        BigInteger energyCost = BigInteger.valueOf(ENERGY_LIMIT).multiply(BigInteger.valueOf(ENERGY_PRICE));
        BigInteger transactionCost = energyCost.add(TRANSFER_AMOUNT);
        BigInteger totalCost = transactionCost.multiply(BigInteger.valueOf(NUM_TRANSACTIONS));
        Assert.assertTrue(INITIAL_SENDER_BALANCE.compareTo(totalCost) >= 0);
    }

    private static void assertAllSendersHaveExpectedBalance(List<PrivateKey> senderKeys) throws InterruptedException {
        List<Address> senders = toAddresses(senderKeys);
        for (Address sender : senders) {
            RpcResult<BigInteger> result = rpc.getBalance(sender);
            Assert.assertTrue(result.isSuccess());
            Assert.assertEquals(INITIAL_SENDER_BALANCE, result.getResult());
        }
    }

    private static List<ProcessedTransactionEventHolder> constructTransactionProcessedEvents(List<SignedTransaction> transactions) {
        List<ProcessedTransactionEventHolder> events = new ArrayList<>();
        for (SignedTransaction transaction : transactions) {
            IEvent transactionIsSealed = prepackagedLogEvents.getTransactionSealedEvent(transaction);
            IEvent transactionIsRejected = prepackagedLogEvents.getTransactionRejectedEvent(transaction);
            IEvent transactionProcessed = Event.or(transactionIsSealed, transactionIsRejected);
            events.add(new ProcessedTransactionEventHolder(transactionIsSealed, transactionIsRejected, transactionProcessed));
        }
        return events;
    }

    private static List<IEvent> extractOnlyTransactionIsProcessedEvent(List<ProcessedTransactionEventHolder> events) {
        List<IEvent> extractedEvents = new ArrayList<>();
        for (ProcessedTransactionEventHolder event : events) {
            extractedEvents.add(event.transactionIsSealedOrRejected);
        }
        return extractedEvents;
    }

    private static List<Address> toAddresses(List<PrivateKey> keys) {
        List<Address> addresses = new ArrayList<>();
        for (PrivateKey key : keys) {
            addresses.add(key.getAddress());
        }
        return addresses;
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
        FileUtils.copyFile(new File(handwrittenConfigs + "/fork.properties"), new File(kernelDirectoryPath + "/networks/custom/fork.properties"));
        FileUtils.copyFile(new File(handwrittenConfigs + "/genesis.json"), new File(kernelDirectoryPath + "/networks/custom/genesis.json"));
        FileUtils.copyFile(new File(handwrittenConfigs + "/config.xml"), new File(kernelDirectoryPath + "/custom/config/config.xml"));
    }

    private static PrivateKey initializePreminedAccount() throws DecoderException, InvalidKeySpecException {
        return PrivateKey.fromBytes(Hex.decodeHex(PREMINED_KEY));
    }

    private static void setRpcPort(String port) throws IOException {
        Charset charset = StandardCharsets.UTF_8;
        Path path = Paths.get(kernelDirectoryPath + "/custom/config/config.xml");
        String content = new String(Files.readAllBytes(path), charset);
        content = content.replaceFirst("<rpc active=\"true\" ip=\"127\\.0\\.0\\.1\" port=\"\\d{4}\">", "<rpc active=\"true\" ip=\"127.0.0.1\" port=\"" + port + "\">");
        content = content.replaceFirst("<java active=\"true\" ip=\"127\\.0\\.0\\.1\" port=\"\\d{4}\">", "<java active=\"true\" ip=\"127.0.0.1\" port=\"2" + port + "\">");
        content = content.replaceFirst("<port>\\d{5}</port>", "<port>3" + port + "</port>");
        Files.write(path, content.getBytes(charset));
    }
}
