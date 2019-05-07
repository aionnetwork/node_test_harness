package org.aion.harness.tests.integ;

import static org.aion.harness.tests.contracts.Assertions.assertRpcSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import org.aion.harness.main.LocalNode;
import org.aion.harness.main.Network;
import org.aion.harness.main.NodeConfigurations;
import org.aion.harness.main.NodeConfigurations.DatabaseOption;
import org.aion.harness.main.NodeFactory;
import org.aion.harness.main.NodeFactory.NodeType;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.ProhibitConcurrentHarness;
import org.aion.harness.main.RPC;
import org.aion.harness.result.Result;
import org.aion.harness.result.RpcResult;
import org.aion.harness.util.SimpleLog;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class PeerConnectTest {
    private static final String BUILT_KERNEL = System.getProperty("user.dir") + "/aion";
    private static final String BUILT_KERNEL2 = System.getProperty("user.dir") + "/aion2";

    private static final SimpleLog log =
            new SimpleLog("org.aion.harness.tests.integ.PeerConnectTest");

    private static LocalNode node;
    private static LocalNode node2;

    private static RPC rpc;
    private static NodeListener listener;

    @BeforeClass
    public static void setup() throws Exception {
        ProhibitConcurrentHarness.acquireTestLock();

        // setup node1
        NodeConfigurations configurations =
                NodeConfigurations.alwaysUseBuiltKernel(
                        Network.CUSTOM, BUILT_KERNEL, DatabaseOption.PRESERVE_DATABASE);
        node = NodeFactory.getNewLocalNodeInstance(NodeType.JAVA_NODE);
        node.configure(configurations);
        Result result = node.initialize();
        log.log(result);
        assertTrue(result.isSuccess());
        Result startResult = node.start();
        assertTrue("Node1 startup error: " + startResult.getError(), startResult.isSuccess());
        assertTrue(node.isAlive());

        // setup node2
        configurations =
                NodeConfigurations.alwaysUseBuiltKernel(
                        Network.CUSTOM, BUILT_KERNEL2, DatabaseOption.PRESERVE_DATABASE);
        node2 = NodeFactory.getNewLocalNodeInstance(NodeType.JAVA_NODE);
        node2.configure(configurations);
        result = node2.initialize();
        log.log(result);
        assertTrue(result.isSuccess());
        startResult = node2.start();
        assertTrue("Node2 startup error: " + startResult.getError(), startResult.isSuccess());
        assertTrue(node2.isAlive());

        rpc = new RPC("127.0.0.1", "18545");
        listener = NodeListener.listenTo(node2);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        System.out.println("Node1 stop: " + node.stop());
        System.out.println("Node2 stop: " + node2.stop());

        node = null;
        node2 = null;
        rpc = null;
        listener = null;
        destroyLogs();
        ProhibitConcurrentHarness.releaseTestLock();
    }

    @Test(timeout = 30000)
    public void testConnectToNode1() throws InterruptedException {
        log.log("get the peerCount...");

        long peerCount = 0;

        while (peerCount == 0) {
            RpcResult<Long> sendResult = rpc.getPeerCount();
            assertRpcSuccess(sendResult);
            peerCount = sendResult.getResult();
            log.log("get the peerCount...");
            Thread.sleep(1000);
        }

        assertEquals(1L, peerCount);
    }

    @Test(timeout = 60000)
    public void tesSyncBlockFromNode1() throws InterruptedException, IOException {

        log.log("clean database in node2");
        cleanDatabase("aion2");
        log.log("sync database...");

        long blockNumber = 0;

        while (blockNumber < 100) {
            RpcResult<Long> sendResult = rpc.blockNumber();
            assertRpcSuccess(sendResult);
            blockNumber = sendResult.getResult();
            log.log("get the blockNumber..." + blockNumber);
            Thread.sleep(1000);
        }
    }

    private static void destroyLogs() throws IOException {
        FileUtils.deleteDirectory(new File(System.getProperty("user.dir") + "/logs"));
    }

    private static void cleanDatabase(String folderPath) throws IOException {
        FileUtils.deleteDirectory(
                new File(System.getProperty("user.dir") + "/" + folderPath + "database"));
    }
}
