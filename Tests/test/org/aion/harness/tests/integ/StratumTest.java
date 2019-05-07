package org.aion.harness.tests.integ;

import static org.aion.harness.tests.contracts.Assertions.assertRpcSuccess;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import org.aion.harness.main.LocalNode;
import org.aion.harness.main.Network;
import org.aion.harness.main.NodeConfigurations;
import org.aion.harness.main.NodeConfigurations.DatabaseOption;
import org.aion.harness.main.NodeFactory;
import org.aion.harness.main.NodeFactory.NodeType;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.ProhibitConcurrentHarness;
import org.aion.harness.main.RPC;
import org.aion.harness.main.types.Block;
import org.aion.harness.main.types.StratumWork;
import org.aion.harness.result.Result;
import org.aion.harness.result.RpcResult;
import org.aion.harness.util.SimpleLog;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class StratumTest {
    private static final String BUILT_KERNEL = System.getProperty("user.dir") + "/aion";

    private static final SimpleLog log = new SimpleLog("org.aion.harness.tests.integ.StratumTest");

    private static LocalNode node;

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

        rpc = new RPC("127.0.0.1", "8545");
        listener = NodeListener.listenTo(node);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        System.out.println("Node1 stop: " + node.stop());

        node = null;
        rpc = null;
        listener = null;
        ProhibitConcurrentHarness.releaseTestLock();
    }

    @Test
    public void getStratumWork() throws InterruptedException {
        log.log("Get the StratumWork...");

        RpcResult<StratumWork> sendResult = rpc.getStratumWork();
        assertRpcSuccess(sendResult);
        StratumWork work = sendResult.getResult();
        log.log("get the work..." + work.toString());

        assertTrue(work.height > 100);

        RpcResult<Block> blockResult = rpc.getBlockByNumber(BigInteger.valueOf(work.height - 1));
        assertRpcSuccess(blockResult);
        Block parantBlock = blockResult.getResult();
        assertArrayEquals(parantBlock.getBlockHash(), work.previousblockhash);
    }
}
