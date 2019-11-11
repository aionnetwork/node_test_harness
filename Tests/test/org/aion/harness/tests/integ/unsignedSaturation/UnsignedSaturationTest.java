package org.aion.harness.tests.integ.unsignedSaturation;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aion.equihash.EquihashMiner;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.BulkRawTransactionBuilder;
import org.aion.harness.kernel.BulkRawTransactionBuilder.TransactionType;
import org.aion.harness.kernel.Kernel;
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
import org.aion.harness.main.impl.JavaNode;
import org.aion.harness.main.types.ReceiptHash;
import org.aion.harness.main.util.TestHarnessHelper;
import org.aion.harness.result.BulkResult;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.Result;
import org.aion.harness.result.RpcResult;
import org.aion.harness.tests.integ.runner.internal.StakingBlockSigner;
import org.aion.harness.tests.integ.runner.internal.UnityBootstrap;
import org.aion.harness.tests.integ.saturation.ProcessedTransactionEventHolder;
import org.aion.harness.tests.integ.saturation.SaturationReport;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A saturation test that uses kernel-side signing for the transactions it sends off.
 * That is, the transactions it sends to the kernel are unsigned and are not signed until they
 * arrive there.
 */
public class UnsignedSaturationTest {
    private static String kernelDirectoryPath = System.getProperty("user.dir") + "/oan";
    private static File kernelDirectory = new File(kernelDirectoryPath);
    private static File handwrittenConfigs = new File(System.getProperty("user.dir") + "/test_resources/custom");
    private static RPC rpc = RPC.newRpc("127.0.0.1", "8545");
    private static JavaNode node;
    private static PrivateKey preminedAccount;
    private static JavaPrepackagedLogEvents prepackagedLogEvents = new JavaPrepackagedLogEvents();
    private static EquihashMiner miner = EquihashMiner.defaultMiner();
    private static StakingBlockSigner signer = StakingBlockSigner.defaultStakingBlockSigner();

    public static final String PASSWORD = "password";
    private static final String PREMINED_KEY = "4c3c8a7c0292bc55d97c50b4bdabfd47547757d9e5c194e89f66f25855baacd0";
    private static final BigInteger INITIAL_SENDER_BALANCE = BigInteger.valueOf(1_000_000_000_000_000_000L).multiply(BigInteger.valueOf(100));
    public static final BigInteger TRANSFER_AMOUNT = BigInteger.valueOf(100);
    public static final long ENERGY_LIMIT = 50_000L;
    public static final long ENERGY_PRICE = 10_000_000_000L;

    // ~~~~~~~ The control variables ~~~~~~~~~
    public static final long THREAD_TIMEOUT_IN_NANOS = TimeUnit.HOURS.toNanos(5);   // Max timeout for waiting for transactions to process.
    public static final long THREAD_DELAY_IN_MILLIS = TimeUnit.SECONDS.toMillis(10); // Time to sleep between checking if all transactions processed.
    private static final int NUM_SENDERS = 100;        // Number of threads sending transactions.
    public static final int NUM_TRANSACTIONS = 10;     // Number of transactions each thread sends.

    @BeforeClass
    public static void setup() throws Exception {
        preminedAccount = PrivateKey.fromBytes(Hex.decodeHex(PREMINED_KEY));
    }

    @AfterClass
    public static void tearDown() throws Exception {
        signer.stop();
        miner.stopMining();
        if ((node != null) && (node.isAlive())) {
            node.stop();
        }
    }

    @Test
    public void saturationTest() throws Exception {
        // Initialize the node.
        checkKernelExistsAndOverwriteConfigs();
        node = initializeNode();

        // 1. Before starting the node we have to create the sender accounts in the keystore.
        System.out.println("Creating the " + NUM_SENDERS + " sender accounts ...");
        long start = System.nanoTime();
        List<Address> senders = createSenderAccounts();
        long end = System.nanoTime();
        long durationInSecs = TimeUnit.NANOSECONDS.toSeconds(end - start);
        System.out.println("Finished creating the sender accounts in " + durationInSecs + " second(s)!");

        // 2. Now start the node up.
        startNode(node);
        miner.startMining();
        UnityBootstrap.bootstrap("8545");
        signer.start();

        // 3. Some basic assertions to catch any silly static errors.
        assertPreminedAccountHasSufficientBalanceToTransfer();
        assertSendersAllHaveSufficientBalanceToSendTransactions();

        // 4. Set up the sender accounts, giving them each enough balance for their transactions.
        System.out.println("Initializing all of the sender accounts with " + INITIAL_SENDER_BALANCE + " balance each ...");
        fundAllSenderAccounts(senders);
        assertAllSendersHaveExpectedBalance(senders);
        System.out.println("All sender accounts initialized!");

        // 5. Start the saturation threads and wait for them to complete.
        System.out.println("Initializing and starting all sender threads ...");
        List<UnsignedSaturator> saturators = createAllSaturators(senders);
        List<FutureTask<SaturationReport>> tasks = createAllFutureTasks(saturators);
        List<Thread> threads = createAllThreads(tasks);
        startAllThreads(threads);
        System.out.println("All sender threads initialized and started!");

        // 6. Collect the reports from the various threads and clean up.
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

    private static void assertPreminedAccountHasSufficientBalanceToTransfer() throws InterruptedException {
        Assert.assertNotNull(preminedAccount);
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

    private static List<Address> createSenderAccounts() throws Exception {
        Assert.assertNotNull(node);
        Kernel kernel = node.getKernel();

        List<Address> accounts = new ArrayList<>();
        for (int i = 0; i < NUM_SENDERS; i++) {
            accounts.add(kernel.createNewAccountInKeystore(PASSWORD));
        }
        return accounts;
    }

    private static void checkKernelExistsAndOverwriteConfigs() throws IOException {
        if (!kernelDirectory.exists() || !kernelDirectory.isDirectory()) {
            throw new IllegalStateException("Expected to find a kernel at: " + kernelDirectoryPath);
        }
        overwriteConfigAndGenesis();
    }

    private static JavaNode initializeNode() throws IOException, InterruptedException {
        NodeConfigurations configurations = NodeConfigurations.alwaysUseBuiltKernel(Network.CUSTOM, kernelDirectoryPath, DatabaseOption.DO_NOT_PRESERVE_DATABASE);
        JavaNode node = (JavaNode) NodeFactory.getNewLocalNodeInstance(NodeType.JAVA_NODE);
        node.configure(configurations);
        Result result = node.initialize();
        if (!result.isSuccess()) {
            throw new IllegalStateException("Failed to initialize node: " + result.getError());
        }
        return node;
    }

    private static void startNode(LocalNode node) throws IOException, InterruptedException {
        Result result = node.start();
        if (!result.isSuccess()) {
            throw new IllegalStateException("Failed to start node: " + result.getError());
        }
    }

    private static void overwriteConfigAndGenesis() throws IOException {
        FileUtils.copyDirectory(handwrittenConfigs, new File(kernelDirectoryPath + "/custom"));
    }

    /**
     * Transfers INITIAL_SENDER_BALANCE amount of aion to each of the listed senders.
     */
    private static void fundAllSenderAccounts(List<Address> senders) throws InterruptedException, TimeoutException {
        Assert.assertNotNull(preminedAccount);

        BulkResult<SignedTransaction> builderResult = new BulkRawTransactionBuilder(NUM_SENDERS)
            .useSameSender(preminedAccount, BigInteger.ZERO)
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

    private static void assertAllSendersHaveExpectedBalance(List<Address> senders) throws InterruptedException {
        for (Address sender : senders) {
            RpcResult<BigInteger> result = rpc.getBalance(sender);
            Assert.assertTrue(result.isSuccess());
            Assert.assertEquals(INITIAL_SENDER_BALANCE, result.getResult());
        }
    }

    private static List<UnsignedSaturator> createAllSaturators(List<Address> senders) {
        CyclicBarrier barrier = new CyclicBarrier(NUM_SENDERS);
        List<UnsignedSaturator> threads = new ArrayList<>();
        for (int i = 0; i < NUM_SENDERS; i++) {
            threads.add(new UnsignedSaturator(i, barrier, senders.get(i)));
        }
        return threads;
    }

    private static List<FutureTask<SaturationReport>> createAllFutureTasks(List<UnsignedSaturator> saturators) {
        List<FutureTask<SaturationReport>> threads = new ArrayList<>();
        for (UnsignedSaturator saturator : saturators) {
            threads.add(new FutureTask<>(saturator));
        }
        return threads;
    }

    private static List<Thread> createAllThreads(List<FutureTask<SaturationReport>> tasks) {
        List<Thread> threads = new ArrayList<>();
        for (FutureTask<SaturationReport> task : tasks) {
            threads.add(new Thread(task));
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
}
