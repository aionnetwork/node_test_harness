package org.aion.harness.tests.integ.saturation;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class SaturationTest {
    private static String kernelDirectoryPath = System.getProperty("user.dir") + "/aion";
    private static File kernelDirectory = new File(kernelDirectoryPath);
    private static File handwrittenConfigs = new File(System.getProperty("user.dir") + "/test_resources/custom");
    private static RPC rpc = RPC.newRpc("127.0.0.1", "8545");
    private static LocalNode node;
    private static JavaPrepackagedLogEvents prepackagedLogEvents = new JavaPrepackagedLogEvents();

    private static final Map<Address, PrivateKey> addressToKeyMap = new HashMap<>();
    private static PrivateKey preminedAccount = null;
    private static BigInteger preminedNonce = BigInteger.ZERO;

    private static final BigInteger INITIAL_SENDER_BALANCE = BigInteger.valueOf(1_000_000_000_000_000_000L).multiply(BigInteger.valueOf(100));
    public static final BigInteger TRANSFER_AMOUNT = BigInteger.valueOf(100);
    public static final long ENERGY_LIMIT = 50_000L;
    public static final long ENERGY_PRICE = 10_000_000_000L;

    // ~~~~~~~ The control variables ~~~~~~~~~
    public static long THREAD_TIMEOUT_IN_NANOS = TimeUnit.HOURS.toNanos(5);   // Max timeout for waiting for transactions to process.
    public static final long THREAD_DELAY_IN_MILLIS = TimeUnit.MINUTES.toMillis(1); // Time to sleep between checking if all transactions processed.
    private static int NUM_SENDERS = 10;        // Number of threads sending transactions.
    public static int NUM_TRANSACTIONS = 10;     // Number of transactions each thread sends.

    @BeforeClass
    public static void setupNode() throws Exception {
        wipeOldCustomDirectory();
        checkKernelExistsAndOverwriteConfigs();
        initializeAddressToKeyMap();

        // Set all of the variables defined by system properties.
        String preminedAddress = System.getProperty("preminedAddress");
        if (preminedAddress == null) {
            throw new IllegalStateException("No premined address specified!");
        }
        preminedAddress = preminedAddress.substring(2);
        preminedAccount = initializePreminedAccount(new Address(Hex.decodeHex(preminedAddress)));

        String timeoutInMins = System.getProperty("threadTimeout");
        if (timeoutInMins == null) {
            throw new IllegalStateException("No thread timeout specified!");
        }
        THREAD_TIMEOUT_IN_NANOS = TimeUnit.MINUTES.toNanos(Long.valueOf(timeoutInMins));

        String numSenders = System.getProperty("numSenders");
        if (numSenders == null) {
            throw new IllegalStateException("No numSenders specified!");
        }
        NUM_SENDERS = Integer.valueOf(numSenders);

        String numTransactions = System.getProperty("numTransactions");
        if (numTransactions == null) {
            throw new IllegalStateException("No numTransactions specified!");
        }
        NUM_TRANSACTIONS = Integer.valueOf(numTransactions);

        // Initialize and start the node.
        node = initializeNode();
        startNode(node);
    }

    @AfterClass
    public static void tearDownNode() throws Exception {
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
        NodeListener listener = NodeListener.listenTo(node);
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
        FileUtils.copyDirectory(handwrittenConfigs, new File(kernelDirectoryPath + "/custom"));
    }

    private static void wipeOldCustomDirectory() throws IOException {
        FileUtils.deleteDirectory(new File(kernelDirectoryPath + "/custom"));
    }

    private static PrivateKey initializePreminedAccount(Address address) {
        return addressToKeyMap.get(address);
    }

    private static void initializeAddressToKeyMap() throws DecoderException, InvalidKeySpecException {
        PrivateKey key1 = PrivateKey.fromBytes(Hex.decodeHex("4c3c8a7c0292bc55d97c50b4bdabfd47547757d9e5c194e89f66f25855baacd0"));
        PrivateKey key2 = PrivateKey.fromBytes(Hex.decodeHex("3de73b2538cd8bfe3db7739305fd85be635e93871a7ea9448198f2eff18ec7e6"));
        PrivateKey key3 = PrivateKey.fromBytes(Hex.decodeHex("ec54eca94898ca13039ec42abf6d901820e41b0af9556da297e23edc8eb42a18"));
        PrivateKey key4 = PrivateKey.fromBytes(Hex.decodeHex("dce0fc2e9a0ce4a46bbb2d9a38498f6ea2fbbdd30b6cf71cd872a1db1695d766"));
        PrivateKey key5 = PrivateKey.fromBytes(Hex.decodeHex("6029cefc7e2ba69f93ed67eced5a443f60c894121dc451c317a1d5f7ceb0bed4"));
        PrivateKey key6 = PrivateKey.fromBytes(Hex.decodeHex("7645e61d78502b99651997dc751e333af270e3d119608fd07bdc4884566e3f97"));
        PrivateKey key7 = PrivateKey.fromBytes(Hex.decodeHex("b094dbf33c707bd4859420939269cabb0798ca51e411f244714b80fce28c0dbe"));
        PrivateKey key8 = PrivateKey.fromBytes(Hex.decodeHex("ebab5b32b41f4c938acabfb321199f01cd132658facc1c8f6bd0310f019d297e"));
        PrivateKey key9 = PrivateKey.fromBytes(Hex.decodeHex("19eb6fba41455858b7a3e0befab17e673a8c8863a928afb81254274d2a1d38ab"));
        PrivateKey key10 = PrivateKey.fromBytes(Hex.decodeHex("deb9f64db0fd2f39d8c4c0c0daa53471cd621f88091aeaab99a4dc7079208349"));
        PrivateKey key11 = PrivateKey.fromBytes(Hex.decodeHex("3580af2d91aeaf53ffb25c4ecd5d2bddffd7a834a61f30baedb61cde1ae5c001"));
        PrivateKey key12 = PrivateKey.fromBytes(Hex.decodeHex("3fe831fa20f6cddacdf8d816bf22275033036b1e49c93aefc66ade2faf081ba4"));
        PrivateKey key13 = PrivateKey.fromBytes(Hex.decodeHex("41036e9ad2aba5ac171fc81498614dbd48e6d7c1f5c7d498647a2ae8aabfe4c9"));
        PrivateKey key14 = PrivateKey.fromBytes(Hex.decodeHex("41c930ffb5b8b8f0cac7da0c8b2f09a477ee91e0a37f66e6b031d8b8672385a8"));
        PrivateKey key15 = PrivateKey.fromBytes(Hex.decodeHex("829b4e8a2238dd68b17bec491bda5f526521febe2e9dd9a2af95994cb36cdc3a"));
        PrivateKey key16 = PrivateKey.fromBytes(Hex.decodeHex("6e0c48f3cdead79414b3275788cfcf3aa9ead04c7e6363a25748823ae392e0d8"));
        PrivateKey key17 = PrivateKey.fromBytes(Hex.decodeHex("9ec5d80349827a0b167507de6b4bd4e06f6e4504b67264c53ae4969d4fd75ad1"));
        PrivateKey key18 = PrivateKey.fromBytes(Hex.decodeHex("48fff5ebc0ce4951b171b1a52229d7b8ab855baa27ea7e91b5225ff051e9186a"));
        PrivateKey key19 = PrivateKey.fromBytes(Hex.decodeHex("4388d3c63f37edd3e144fc81be15313b8baff64c9feafd192c5716b8d798f2c7"));
        PrivateKey key20 = PrivateKey.fromBytes(Hex.decodeHex("2584d45cd5e2dcca80d5271bbec28de5962d230cf4432e3c5598d73300101cf6"));
        PrivateKey key21 = PrivateKey.fromBytes(Hex.decodeHex("a37cea93bfd640722a7d5a1b0b90e54b0bc2394ab0dedba2affb6a0ce292d3c7"));
        PrivateKey key22 = PrivateKey.fromBytes(Hex.decodeHex("96757b88cae4b5802519b7942efa3b123bb8b87e2998abde4c015daa96dce718"));
        PrivateKey key23 = PrivateKey.fromBytes(Hex.decodeHex("fba992cb1067341745a035ad083e6d724dcb2e34f367c2a62848e67649c161e2"));
        PrivateKey key24 = PrivateKey.fromBytes(Hex.decodeHex("97552c33b905dec1d9c15247219dc4c115aa78fbead22fc4c5deb49222a0c96e"));
        PrivateKey key25 = PrivateKey.fromBytes(Hex.decodeHex("bacdc56753663e66bd484dc570d0e7072701f7dfc57ed2a005f70da446b0412c"));
        PrivateKey key26 = PrivateKey.fromBytes(Hex.decodeHex("2d86d92879f782ee2ddbb321385ae58c2622f3883e86a6e2a78428056253f1c2"));
        PrivateKey key27 = PrivateKey.fromBytes(Hex.decodeHex("1d572ec6f3fa2dc02f409060432c2a5f7d3ee6d9d36bfee55fb8e6d960dab727"));
        PrivateKey key28 = PrivateKey.fromBytes(Hex.decodeHex("568f079102500516ea45bfdc840d101a76878c1c94149737469a4c4170339372"));
        PrivateKey key29 = PrivateKey.fromBytes(Hex.decodeHex("76870f11888e816766fde09606ae1950acf9857b3d22816c67c350f95522611e"));
        PrivateKey key30 = PrivateKey.fromBytes(Hex.decodeHex("f0dea349d57666aef81c1c2ea8b6ddb4627d65f6fb32af8362f3c16dce2ca2b5"));
        addressToKeyMap.put(key1.getAddress(), key1);
        addressToKeyMap.put(key2.getAddress(), key2);
        addressToKeyMap.put(key3.getAddress(), key3);
        addressToKeyMap.put(key4.getAddress(), key4);
        addressToKeyMap.put(key5.getAddress(), key5);
        addressToKeyMap.put(key6.getAddress(), key6);
        addressToKeyMap.put(key7.getAddress(), key7);
        addressToKeyMap.put(key8.getAddress(), key8);
        addressToKeyMap.put(key9.getAddress(), key9);
        addressToKeyMap.put(key10.getAddress(), key10);
        addressToKeyMap.put(key11.getAddress(), key11);
        addressToKeyMap.put(key12.getAddress(), key12);
        addressToKeyMap.put(key13.getAddress(), key13);
        addressToKeyMap.put(key14.getAddress(), key14);
        addressToKeyMap.put(key15.getAddress(), key15);
        addressToKeyMap.put(key16.getAddress(), key16);
        addressToKeyMap.put(key17.getAddress(), key17);
        addressToKeyMap.put(key18.getAddress(), key18);
        addressToKeyMap.put(key19.getAddress(), key19);
        addressToKeyMap.put(key20.getAddress(), key20);
        addressToKeyMap.put(key21.getAddress(), key21);
        addressToKeyMap.put(key22.getAddress(), key22);
        addressToKeyMap.put(key23.getAddress(), key23);
        addressToKeyMap.put(key24.getAddress(), key24);
        addressToKeyMap.put(key25.getAddress(), key25);
        addressToKeyMap.put(key26.getAddress(), key26);
        addressToKeyMap.put(key27.getAddress(), key27);
        addressToKeyMap.put(key28.getAddress(), key28);
        addressToKeyMap.put(key29.getAddress(), key29);
        addressToKeyMap.put(key30.getAddress(), key30);
    }
}
