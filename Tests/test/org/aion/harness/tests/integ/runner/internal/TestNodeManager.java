package org.aion.harness.tests.integ.runner.internal;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.aion.harness.main.LocalNode;
import org.aion.harness.main.Network;
import org.aion.harness.main.NodeConfigurations;
import org.aion.harness.main.NodeConfigurations.DatabaseOption;
import org.aion.harness.main.NodeFactory;
import org.aion.harness.main.NodeFactory.NodeType;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.ProhibitConcurrentHarness;
import org.aion.harness.result.Result;
import org.aion.harness.tests.integ.runner.exception.TestRunnerInitializationException;
import org.apache.commons.io.FileUtils;

/**
 * This class is responsible for the lifecycle of the node that all tests are running against.
 *
 * The node is never exposed. The caller has the ability to start and stop it.
 */
public final class TestNodeManager {
    private NodeType nodeType;
    private LocalNode localNode;
    private final String expectedKernelLocation;
    private final String handedwrittenConfigs;

    private static final String WORKING_DIR = System.getProperty("user.dir");
    private static final long EXIT_LOCK_TIMEOUT = 3;
    private static final TimeUnit EXIT_LOCK_TIMEOUT_UNIT = TimeUnit.MINUTES;

    public TestNodeManager(NodeType nodeType) {
        this.nodeType = nodeType;
        if(nodeType == NodeType.RUST_NODE) {
            this.expectedKernelLocation = WORKING_DIR + "/aionr";
            this.handedwrittenConfigs = WORKING_DIR + "/test_resources/rust_custom";
        } else if(nodeType == NodeType.JAVA_NODE) {
            this.expectedKernelLocation = WORKING_DIR + "/aion";
            this.handedwrittenConfigs = WORKING_DIR + "/test_resources/custom";
        } else if(nodeType == NodeType.PROXY_JAVA_NODE) {
            this.expectedKernelLocation = WORKING_DIR + "/aionproxy";
            this.handedwrittenConfigs = WORKING_DIR + "/test_resources/proxy_java_custom";
        } else {
            throw new IllegalArgumentException("Unsupported kernel");
        }
    }

    /**
     * Starts up a local node of the specified type if no node has currently been started.
     *
     * If anything goes wrong this method will throw an exception to halt the runner.
     */
    public void startLocalNode() throws Exception {
        if (this.localNode == null) {
            // Verify the kernel is in the expected location and overwrite its config & genesis files.
            checkKernelExistsAndOverwriteConfigs();
            checkKernelExistsAndDeleteDBifJava();
            

            // Acquire the system-wide lock.
            ProhibitConcurrentHarness.acquireTestLock();

            // Initialize the node.
            NodeConfigurations configurations = NodeConfigurations.alwaysUseBuiltKernel(
                Network.CUSTOM, expectedKernelLocation, DatabaseOption.PRESERVE_DATABASE);
            LocalNode node = NodeFactory.getNewLocalNodeInstance(nodeType);
            node.configure(configurations);

            Result result = node.initialize();
            if (!result.isSuccess()) {
                throw new TestRunnerInitializationException("Failed to initialize the node: " + result.getError());
            }

            // Start the node.
            result = node.start();
            if (!result.isSuccess()) {
                throw new TestRunnerInitializationException("Failed to start the node: " + result.getError());
            }

            this.localNode = node;
        } else {
            throw new IllegalStateException("Attempted to start running a local node but one is already running!");
        }
    }

    /**
     * Stops a local node if one is currently running.
     */
    public void shutdownLocalNode() throws Exception {
        if (this.localNode != null) {
            try {
                this.localNode.blockingStop(EXIT_LOCK_TIMEOUT, EXIT_LOCK_TIMEOUT_UNIT);
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                ProhibitConcurrentHarness.releaseTestLock();
                this.localNode = null;
            }
        } else {
            throw new IllegalStateException("Attempted to stop running a local node but no node is currently running!");
        }
    }

    /**
     * Returns a newly created node listener that is listening to the current running local node.
     */
    public NodeListener newNodeListener() {
        if (this.localNode != null) {
            return NodeListener.listenTo(this.localNode);
        } else {
            throw new IllegalStateException("Attempted to get a new listener but no local node is currently running!");
        }
    }

    private void checkKernelExistsAndDeleteDBifJava() throws IOException {
        if (!kernelExists()) {
            throw new TestRunnerInitializationException("Expected to find a kernel at: " + expectedKernelLocation);
        }
        if(nodeType == NodeType.JAVA_NODE
                || nodeType == NodeType.PROXY_JAVA_NODE ) {
            FileUtils.deleteDirectory(new File(expectedKernelLocation + "/custom/database"));
        }
    }

    private void checkKernelExistsAndOverwriteConfigs() throws IOException {
        if (!kernelExists()) {
            throw new TestRunnerInitializationException("Expected to find a kernel at: " + expectedKernelLocation);
        }
        overwriteConfigAndGenesis();
    }

    private boolean kernelExists() {
        File kernel = new File(expectedKernelLocation);
        return kernel.exists() && kernel.isDirectory();
    }

    private void overwriteConfigAndGenesis() throws IOException {
        if(nodeType == NodeType.RUST_NODE) {
            overwriteIfTargetDirExists(new File(handedwrittenConfigs),
                new File(expectedKernelLocation + "/custom"));
        } else if(nodeType == NodeType.JAVA_NODE
            || nodeType == NodeType.PROXY_JAVA_NODE ) {
            FileUtils.copyDirectory(new File(handedwrittenConfigs),
                new File(expectedKernelLocation + "/custom"));
        } else {
            throw new IllegalStateException("Unsupported kernel");
        }
    }

    private static void overwriteIfTargetDirExists(File source, File target) throws IOException {
        if (target.exists()) {
            FileUtils.copyDirectory(source, target);
        }
    }
}
