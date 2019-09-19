package org.aion.harness.tests.integ.saturation;

import java.math.BigInteger;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.ReentrantLock;
import org.aion.harness.kernel.PrivateKey;
import org.apache.commons.codec.binary.Hex;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class SaturationTest {
    public volatile static PrivateKey preminedAccount = null;
    private static final BigInteger PREMINED_BALANCE = new BigInteger("3140000000000000000000000000000000");
    public static final BigInteger INITIAL_SENDER_BALANCE = new BigInteger("325000000000000650000000000");
    public static final BigInteger TRANSFER_AMOUNT = BigInteger.ONE;
    public static final long ENERGY_LIMIT = 50_000L;
    public static final long ENERGY_PRICE = 10_000_000_000L;

    // ~~~~~~~ The control variables ~~~~~~~~~
    public static Integer NUM_TRANSACTIONS = null;     // Number of transactions each thread sends.
    public static Integer TRANSACTIONS_PER_SECOND = null;
    private static List<String> ipAddresses = null;
    private static ReentrantLock nonceLock = new ReentrantLock();
    private static BigInteger preminedNonce = BigInteger.ZERO;

    public static BigInteger getAndIncrementNonce() {
        nonceLock.lock();
        BigInteger nonce = preminedNonce;
        preminedNonce = preminedNonce.add(BigInteger.ONE);
        nonceLock.unlock();
        return nonce;
    }

    @BeforeClass
    public static void setup() {
        String numTransactions = System.getProperty("numTransactions");
        if (numTransactions == null) {
            throw new IllegalStateException("No numTransactions specified!");
        }
        NUM_TRANSACTIONS = Integer.valueOf(numTransactions);
        Assert.assertNotNull(NUM_TRANSACTIONS);

        String transactionsPerSecond = System.getProperty("transactionsPerSecond");
        if (transactionsPerSecond == null) {
            throw new IllegalStateException("No transactionsPerSecond specified!");
        }
        TRANSACTIONS_PER_SECOND = Integer.valueOf(transactionsPerSecond);
        Assert.assertNotNull(TRANSACTIONS_PER_SECOND);

        String IPs = System.getProperty("ipAddresses");
        if (IPs == null) {
            throw new IllegalStateException("No IP addresses specified!");
        }
        ipAddresses = parseIPs(IPs);
        Assert.assertNotNull(ipAddresses);
        Assert.assertFalse(ipAddresses.isEmpty());

        System.out.println("Using the following IP addresses: " + ipAddresses);
    }

    private static List<String> parseIPs(String IPs) {
        return Arrays.asList(IPs.split(", "));
    }

    @Test
    public void saturationTest() throws Exception {
        preminedAccount = PrivateKey.fromBytes(Hex.decodeHex("4c3c8a7c0292bc55d97c50b4bdabfd47547757d9e5c194e89f66f25855baacd0"));
        Assert.assertNotNull(preminedAccount);

        if (PREMINED_BALANCE.compareTo(INITIAL_SENDER_BALANCE.multiply(BigInteger.valueOf(30))) < 0) {
            throw new IllegalStateException("Premined account does not have enough balance to fund all senders!");
        }

        // Set up the sender accounts, giving them each enough balance for their transactions.
        System.out.println("Initializing all of the sender accounts with " + INITIAL_SENDER_BALANCE + " balance each ...");
        List<PrivateKey> senderKeys = newRandomKeys(ipAddresses.size());
        Assert.assertEquals(ipAddresses.size(), senderKeys.size());
        System.out.println("All sender accounts initialized!");

        // Start the node sender threads.
        System.out.println("Starting all sender threads ...");
        List<NodeSender> nodeSenders = createAllNodeSenders(senderKeys);
        List<Thread> threads = createAllNodeSenderThreads(nodeSenders);
        startAllThreads(threads);
        System.out.println("All sender threads started!");

        System.out.println("Waiting for all threads to finish...");
        for (Thread thread : threads) {
            thread.join();
        }
        for (NodeSender nodeSender : nodeSenders) {
            Exception error = nodeSender.error;
            if (error != null) {
                System.out.println("Thread " + nodeSender.name + " exited with an error: " + nodeSender.error.getMessage());
                error.printStackTrace();
            }
        }
        System.out.println("All threads have finished sending transactions.");
    }

    private static List<NodeSender> createAllNodeSenders(List<PrivateKey> senderKeys) {
        CyclicBarrier barrier = new CyclicBarrier(ipAddresses.size());
        List<NodeSender> threads = new ArrayList<>();
        for (int i = 0; i < ipAddresses.size(); i++) {
            threads.add(new NodeSender(i, barrier, ipAddresses.get(i), senderKeys.get(i)));
        }
        return threads;
    }

    private static List<Thread> createAllNodeSenderThreads(List<NodeSender> nodeSenders) {
        List<Thread> threads = new ArrayList<>();
        for (NodeSender nodeSender : nodeSenders) {
            threads.add(new Thread(nodeSender));
        }
        return threads;
    }

    private static void startAllThreads(List<Thread> threads) {
        for (Thread thread : threads) {
            thread.start();
        }
    }

    private static List<PrivateKey> newRandomKeys(int num) throws InvalidKeySpecException {
        List<PrivateKey> keys = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            keys.add(PrivateKey.random());
        }
        return keys;
    }
}
