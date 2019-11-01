package org.aion.harness.integ;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.aion.harness.integ.resources.Eavesdropper;
import org.aion.harness.integ.resources.Eavesdropper.Gossip;
import org.aion.harness.integ.resources.TestHelper;
import org.aion.harness.main.LocalNode;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.event.JavaPrepackagedLogEvents;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.Result;
import org.aion.harness.util.NodeFileManager;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

public class NodeListenerLifecycleTest {
    private LocalNode node;

    @Before
    public void setup() throws IOException, InterruptedException {
        this.node = TestHelper.configureDefaultLocalNodeAndDoNotPreserveDatabase();
        assertTrue(this.node.initialize().isSuccess());
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        shutdownNodeIfRunning();
        this.node = null;
    }

    @AfterClass
    public static void tearDownAfterAllTests() throws IOException {
        deleteLogs();
    }

    @Test
    public void testNodeListenerBeforeStartingNode() throws InterruptedException {
        NodeListener listener = NodeListener.listenTo(this.node.getID());

        // Time out should be irrelevant in this situation.
        LogEventResult result = listener.listenForEvent(
            new JavaPrepackagedLogEvents().getHeartbeatEvent(),
            10,
            TimeUnit.SECONDS).get();
        System.out.println("Result = " + result);
        assertTrue(result.eventWasRejected());
        assertEquals("Listener is not currently listening to a log file.", result.causeOfRejection());
    }

    @Test
    public void testNodeListenerAfterShuttingDownNode() throws IOException, InterruptedException {
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        NodeListener listener = NodeListener.listenTo(this.node.getID());

        Thread.sleep(TimeUnit.SECONDS.toMillis(10));

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());

        LogEventResult eventResult = listener.listenForEvent(
            new JavaPrepackagedLogEvents().getHeartbeatEvent(),
            10,
            TimeUnit.SECONDS).get();
        System.out.println("Result = " + eventResult);
        assertTrue(eventResult.eventWasRejected());
        assertEquals("Listener is not currently listening to a log file.", eventResult.causeOfRejection());
    }

    /**
     * In this case the node should continue being able to run, but the log reader will panic and
     * shut down.
     *
     * As a result, the {@link NodeListener} will have any outstanding requests rejected due to the
     * panic, and all subsequent requests will be immediately rejected.
     */
    @Test
    public void testDeletingLogFileWhileNodeIsRunning() throws IOException, InterruptedException {

        // Start the node.
        Result result = this.node.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.node.isAlive());

        // We launch the listener on a separate thread so that we don't get blocked by it.
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Eavesdropper eavesdropper = Eavesdropper.createEavesdropperThatListensFor(Gossip.UNSPEAKABLE, 0, this.node.getID());
        executor.execute(eavesdropper);

        assertTrue(eavesdropper.isAlive());

        // Tell the eavesdropper to freeze its latest result, we want to view its very first result only.
        eavesdropper.freezeLatestResult();

        // The listener is now listening for an event that will not occur, with a large timeout.
        // We now want to delete the log file out from underneath the listener.
        File logDir = new File(System.getProperty("user.dir") + File.separator + "logs");
        File[] entries = logDir.listFiles();
        assertNotNull(entries);

        File outputLog = null;
        for (File entry : entries) {
            if (entry.getName().contains("out")) {
                outputLog = entry;
            }
        }
        assertNotNull(outputLog);

        // Wait until the request is in the pool.
        long timeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
        while ((System.currentTimeMillis() < timeout) && (NodeListener.listenTo(this.node.getID()).numberOfEventsBeingListenedFor() == 0)) {
            Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        }

        if (NodeListener.listenTo(this.node.getID()).numberOfEventsBeingListenedFor() == 0) {
            fail("Timed out waiting for the eavesdropper to submit its request!");
        }

        // Now we can delete the log and confirm it has been deleted.
        assertTrue(outputLog.delete());

        // Ensure the listener thread is still running and collect the result of its request.
        // The request may not be in yet, so we wait until it is.
        assertTrue(eavesdropper.isAlive());

        timeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
        LogEventResult eventResult = null;
        while ((System.currentTimeMillis() < timeout) && (eventResult == null)) {
            eventResult = eavesdropper.fetchLatestResult();
            Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        }

        if (eventResult == null) {
            fail("Timed out waiting for the result from the eavesdropper!");
        }

        // This event should be rejected due to the file being deleted (which the listener will initially
        // interpret as a log rotation).
        assertTrue(eventResult.eventWasRejected());
        assertTrue(eventResult.causeOfRejection().contains("Log file not found!"));
        System.out.println("Event result -> " + eventResult);

        // Now unfreeze the latest result, the eavesdropper should continue listening and should be
        // getting rejected immediately because it is in an unrecoverable state.
        eavesdropper.unfreezeLatestResult();

        // Sleep a bit to let the other requests get through. They should be rejected immediately,
        // this is just to be safe.
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));

        eventResult = eavesdropper.fetchLatestResult();
        assertTrue(eventResult.eventWasRejected());
        assertEquals("Listener is not currently listening to a log file.", eventResult.causeOfRejection());
        System.out.println("Latest result -> " + eventResult);

        // Verify that the node is still running fine.
        assertTrue(this.node.isAlive());

        // Shut the thread down and the node.
        eavesdropper.kill();
        executor.shutdownNow();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        result = this.node.stop();
        System.out.println("Stop result = " + result);

        assertTrue(result.isSuccess());
        assertFalse(this.node.isAlive());
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
