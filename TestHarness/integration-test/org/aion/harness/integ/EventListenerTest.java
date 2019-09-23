package org.aion.harness.integ;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.aion.harness.integ.resources.TestHelper;
import org.aion.harness.kernel.Address;
import org.aion.harness.kernel.PrivateKey;
import org.aion.harness.kernel.SignedTransaction;
import org.aion.harness.main.LocalNode;
import org.aion.harness.main.Network;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.RPC;
import org.aion.harness.main.event.Event;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.event.JavaPrepackagedLogEvents;
import org.aion.harness.result.FutureResult;
import org.aion.harness.misc.Assumptions;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.Result;
import org.aion.harness.util.NodeFileManager;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EventListenerTest {
    private static Address destination;
    private static PrivateKey preminedPrivateKey;

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
    public void testWaitForMinersToStart() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        NodeListener listener = NodeListener.listenTo(this.node);

        LogEventResult requestResult = listener.listenForEvent(new JavaPrepackagedLogEvents().getStartedMiningEvent(), 2, TimeUnit.MINUTES).get();

        System.out.println(requestResult);
        Assert.assertTrue(requestResult.eventWasObserved());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testTransactionProcessed() throws Exception {
        SignedTransaction transaction = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.ZERO);

        this.node.initialize();
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        NodeListener listener = NodeListener.listenTo(this.node);

        FutureResult<LogEventResult> futureResult = listener.listenForEvent(
            new JavaPrepackagedLogEvents().getTransactionProcessedEvent(transaction),
            2,
            TimeUnit.MINUTES);

        this.rpc.sendSignedTransaction(transaction);

        LogEventResult requestResult = futureResult.get();

        System.out.println(requestResult);
        Assert.assertTrue(requestResult.eventWasObserved());

        List<String> observed = requestResult.getAllObservedEvents();
        assertEquals(1, observed.size());
        assertTrue(observed.get(0).contains("sealed"));

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());

        Thread.sleep(15000);

        // ------------------------------------------------------------------------------

        transaction = constructTransaction(
            preminedPrivateKey,
            destination,
            BigInteger.ONE,
            BigInteger.ONE);

        System.out.println(this.node.start());
        Assert.assertTrue(this.node.isAlive());

        listener = NodeListener.listenTo(this.node);

        futureResult = listener.listenForEvent(
            new JavaPrepackagedLogEvents().getTransactionProcessedEvent(transaction),
            2,
            TimeUnit.MINUTES);

        this.rpc.sendSignedTransaction(transaction);

        requestResult = futureResult.get();

        System.out.println(requestResult);
        Assert.assertTrue(requestResult.eventWasObserved());

        observed = requestResult.getAllObservedEvents();
        assertEquals(1, observed.size());
        assertTrue(observed.get(0).contains("sealed"));

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testWaitForTransactionToBeRejected() throws DecoderException, IOException, InterruptedException, InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        // create a private key, has zero balance, sending balance from it would cause transaction to fail
        PrivateKey privateKeyWithNoBalance = PrivateKey.fromBytes(Hex.decodeHex("00e9f9800d581246a9665f64599f405e8927993c6bef4be2776d91a66b466d30"));

        SignedTransaction transaction = constructTransaction(
            privateKeyWithNoBalance,
            destination,
            BigInteger.ONE,
            BigInteger.ZERO);

        this.node.initialize();
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        NodeListener listener = NodeListener.listenTo(this.node);

        FutureResult<LogEventResult> futureResult = listener.listenForEvent(
            new JavaPrepackagedLogEvents().getTransactionProcessedEvent(transaction),
            2,
            TimeUnit.MINUTES);

        this.rpc.sendSignedTransaction(transaction);

        LogEventResult requestResult = futureResult.get();

        System.out.println(requestResult);
        Assert.assertTrue(requestResult.eventWasObserved());

        // check it was a rejected event that was observed
        List<String> observed = requestResult.getAllObservedEvents();
        assertEquals(1, observed.size());
        assertTrue(observed.get(0).contains("rejected"));

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testFutureResultBlocksOnGet() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        NodeListener listener = NodeListener.listenTo(this.node);
        IEvent event = new Event("I will never occur");

        long duration = 10;

        long start = System.nanoTime();
        listener.listenForEvent(event, duration, TimeUnit.SECONDS).get();
        long end = System.nanoTime();

        // Basically we expect to wait approximately duration seconds since this is a blocking call.
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(end - start) >= (duration - 1));

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
    }

    @Test
    public void testListenForDoesNotBlock() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        NodeListener listener = NodeListener.listenTo(this.node);
        IEvent event = new Event("I will never occur");

        long duration = 10;

        long start = System.nanoTime();
        FutureResult<LogEventResult> futureResult = listener.listenForEvent(event, duration, TimeUnit.SECONDS);
        long end = System.nanoTime();

        // Basically we expect to return much earlier than the duration. Using less than here just
        // as in opposition to the blocking event test above & to avoid making assumptions.
        // So long as the event never does occur this property is strong enough.
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(end - start) < (duration - 1));

        // Verify that we did in fact expire
        assertTrue(futureResult.get().eventExpired());

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
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
            System.out.println("Stop result = " + result);

            assertTrue(result.isSuccess());
            assertFalse(this.node.isAlive());
        }
    }
}
