package org.aion.harness.integ;

import java.util.concurrent.TimeoutException;
import org.aion.harness.integ.resources.LogWriter;
import org.aion.harness.integ.resources.TestHelper;
import org.aion.harness.main.LocalNode;
import org.aion.harness.main.NodeFactory;
import org.aion.harness.main.NodeFactory.NodeType;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.event.Event;
import org.aion.harness.result.FutureResult;

import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.Result;
import org.aion.harness.util.NodeFileManager;
import org.aion.harness.main.impl.GenericRemoteNode;
import org.apache.commons.io.FileUtils;
import org.junit.*;
import java.io.File;
import java.io.IOException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.*;


public class RemoteNodeTest {
    private GenericRemoteNode remoteNode;
    private LocalNode localNode;

    @Before
    public void setup() throws IOException, InterruptedException {
        this.remoteNode = NodeFactory.getNewGenericRemoteNodeInstance(NodeType.JAVA_NODE);
        this.localNode = TestHelper.configureDefaultLocalNodeAndDoNotPreserveDatabase();
        assertTrue(this.localNode.initialize().isSuccess());
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        shutdownNodeIfRunning();
        this.remoteNode = null;
        this.localNode = null;
    }

    @AfterClass
    public static void tearDownAfterAllTests() throws IOException {
        deleteLogs();
    }

    @Test (expected = NullPointerException.class)
    public void testConnectNullFile() {
        this.remoteNode.connect(null);
    }

    @Test (expected = IllegalArgumentException.class)
    public void testConnectFileNotExist() throws IOException {
        File fileNotExist = new File(NodeFileManager.getLogsDirectory().getCanonicalPath() + File.separator + "notExist");
        this.remoteNode.connect(fileNotExist);
    }

    @Test (expected = IllegalArgumentException.class)
    public void testConnectLogIsNotAFile() {
        File notAFile = new File(NodeFileManager.getLogsDirectory().getAbsolutePath() + File.separator + "newDirectory");
        assertTrue(notAFile.mkdir());

        this.remoteNode.connect(notAFile);
    }

    @Test
    public void testConnectRemoteNodeToLocalNodeLogFile()
        throws IOException, InterruptedException, TimeoutException {
        Result result = this.localNode.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.localNode.isAlive());

        // get the log file and connect with remote node
        File localNodeLogFile = null;
        File[] logFiles = NodeFileManager.getLogsDirectory().listFiles();
        assertNotNull(logFiles);
        for (File file: logFiles) {
            if (file.getName().contains("out")) {
                localNodeLogFile = file;
            }
        }

        result = this.remoteNode.connect(localNodeLogFile);
        System.out.println("Connect result = " + result);
        assertTrue(result.isSuccess());

        NodeListener nodeListener = NodeListener.listenTo(this.remoteNode);
        FutureResult<LogEventResult> future = nodeListener.listenForEvent(new Event("DEBUG"), 60, TimeUnit.SECONDS);

        LogEventResult waitResult = future.get(120, SECONDS);
        System.out.println("Listener result = " + future.toString());
        assertTrue(waitResult.eventWasObserved());

        this.remoteNode.disconnect();
        this.localNode.stop();
    }

    @Test
    public void testRemoteListenerWhenRemoteNodeIsDisconnected() throws IOException, InterruptedException {
        // create a log file
        File localNodeLogFile = createRemoteLogFile();

        // start the log writer with a new thread
        LogWriter logWriter = new LogWriter(localNodeLogFile);
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.execute(logWriter);
        logWriter.start();

        // sleep and wait for some logs to be written
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));

        Result result = this.remoteNode.connect(localNodeLogFile);
        System.out.println("Connect result = " + result);
        assertTrue(result.isSuccess());

        // disconnect the remote node
        this.remoteNode.disconnect();

        // try to listen when listener has stopped listening
        NodeListener nodeListener = NodeListener.listenTo(this.remoteNode);
        FutureResult<LogEventResult> futureResult = nodeListener.listenForEvent(new Event("message"), 30, TimeUnit.SECONDS);

        // get the result of the listener.
        LogEventResult waitResult = futureResult.get();
        System.out.println("Listener result = " + futureResult.toString());
        assertTrue(waitResult.eventWasRejected());

        // shutdown thread
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }

    @Test
    public void testRemoteNode() throws InterruptedException, IOException {
        String message = "listen to this message";

        // create a log file
        File outputLogFile = createRemoteLogFile();

        // start the log writer with a new thread
        LogWriter logWriter = new LogWriter(outputLogFile);
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.execute(logWriter);
        logWriter.start();

        // set up remote node and connect to log file
        Result connectionResult = this.remoteNode.connect(outputLogFile);
        assertTrue(connectionResult.isSuccess());

        // sleep and wait for some logs to be written
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));

        // Start listening for the message we are going to set.
        NodeListener nodeListener = NodeListener.listenTo(this.remoteNode);
        FutureResult<LogEventResult> futureResult = nodeListener.listenForEvent(new Event(message), 1, TimeUnit.MINUTES);

        // put a message to the logs, this is the message we are looking for.
        logWriter.setListenMessage(message);

        // get the result of the listener.
        LogEventResult waitResult = futureResult.get();

        System.out.println("Listener result = " + futureResult.toString());
        assertTrue(waitResult.eventWasObserved());

        // disconnect the node and shut down the thread.
        remoteNode.disconnect();
        logWriter.kill();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }

    @Test
    public void testLocalAndRemoteNodeTogether()
        throws InterruptedException, IOException, TimeoutException {
        String message = "my message";

        // start local node
        Result result = this.localNode.start();
        System.out.println("Start result = " + result);

        assertTrue(result.isSuccess());
        assertTrue(this.localNode.isAlive());

        // create a log file for the remote node to listen to.
        File outputLogFile = createRemoteLogFile();

        // start the log writer with a new thread
        LogWriter logWriter = new LogWriter(outputLogFile);
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.execute(logWriter);
        logWriter.start();

        // set up remote node and connect to log file
        Result connectionResult = this.remoteNode.connect(outputLogFile);
        assertTrue(connectionResult.isSuccess());

        // sleep and wait for some logs to be written
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));

        // Listen to the local node.
        NodeListener localNodeListener = NodeListener.listenTo(this.localNode);
        FutureResult<LogEventResult> localFuture = localNodeListener.listenForEvent(new Event("DEBUG"), 1, TimeUnit.MINUTES);

        // Listen to the remote node.
        NodeListener remoteNodeListener = NodeListener.listenTo(this.remoteNode);
        FutureResult<LogEventResult> remoteFuture = remoteNodeListener.listenForEvent(new Event(message), 1, TimeUnit.MINUTES);

        // put a message to the logs, this is the message we are looking for.
        logWriter.setListenMessage(message);

        // get the results of the listeners.
        LogEventResult localResult = localFuture.get(120, SECONDS);
        LogEventResult remoteResult = remoteFuture.get(120, SECONDS);

        System.out.println("Local listener result = " + localFuture);
        System.out.println("Remote listener result = " + remoteFuture);

        assertTrue(localResult.eventWasObserved());
        assertTrue(remoteResult.eventWasObserved());

        // stop local node
        result = this.localNode.stop();
        System.out.println("Stop result = " + result);
        assertTrue(result.isSuccess());
        assertFalse(this.localNode.isAlive());

        // disconnect remote node and shut down the thread.
        this.remoteNode.disconnect();
        logWriter.kill();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }

    @Test (expected = IllegalStateException.class)
    public void testConnectMultipleTimesToRemoteNode() throws IOException {
        // create a log file
        File outputLogFile = createRemoteLogFile();

        // start the log writer with a new thread
        LogWriter logWriter = new LogWriter(outputLogFile);
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.execute(logWriter);
        logWriter.start();

        // connect to log file - once
        Result connectionResult = this.remoteNode.connect(outputLogFile);
        assertTrue(connectionResult.isSuccess());

        // connect to log file - again
        connectionResult = this.remoteNode.connect(outputLogFile);
        assertTrue(connectionResult.isSuccess());
    }

    @Test
    public void testDisconnectMultipleTimesToRemoteNode() throws IOException, InterruptedException {
        // create a log file
        File outputLogFile = createRemoteLogFile();

        // start the log writer with a new thread
        LogWriter logWriter = new LogWriter(outputLogFile);
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.execute(logWriter);
        logWriter.start();

        // connect to log file
        Result connectionResult = this.remoteNode.connect(outputLogFile);
        assertTrue(connectionResult.isSuccess());

        // disconnect - once
        Result disconnectResult = this.remoteNode.disconnect();
        assertTrue(disconnectResult.isSuccess());

        // disconnect - once again
        disconnectResult = this.remoteNode.disconnect();
        assertTrue(disconnectResult.isSuccess());
    }

    private File createRemoteLogFile() throws IOException {
        File logDirectory = NodeFileManager.getLogsDirectory();
        File outputLogFile = new File(NodeFileManager.getLogsDirectory() + File.separator + "testRemoteNode.txt");
        if (!logDirectory.exists()) {
            assertTrue(logDirectory.mkdir());
        }

        if (!outputLogFile.exists()) {
            assertTrue(outputLogFile.createNewFile());
        }

        return outputLogFile;
    }

    private static void deleteLogs() throws IOException {
        FileUtils.deleteDirectory(NodeFileManager.getLogsDirectory());
    }

    private void shutdownNodeIfRunning() throws IOException, InterruptedException {
        if ((this.localNode != null) && (this.localNode.isAlive())) {
            Result result = this.localNode.stop();
            System.out.println("Stop result = " + result);

            assertTrue(result.isSuccess());
            assertFalse(this.localNode.isAlive());
        }
    }
}
