package org.aion.harness.tests.integ.runner.internal;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.aion.harness.main.LocalNode;
import org.aion.harness.main.Network;
import org.aion.harness.main.NodeConfigurations;
import org.aion.harness.main.NodeConfigurations.DatabaseOption;
import org.aion.harness.main.NodeFactory;
import org.aion.harness.main.NodeFactory.NodeType;
import org.aion.harness.main.NodeListener;
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
            this.expectedKernelLocation = WORKING_DIR + "/oan";
            this.handedwrittenConfigs = WORKING_DIR + "/test_resources/custom/config";
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

            setRpcPort(System.getProperty("rpcPort"));

            // Initialize the node.
            NodeConfigurations configurations = NodeConfigurations.alwaysUseBuiltKernel(
                Network.CUSTOM, expectedKernelLocation, DatabaseOption.DO_NOT_PRESERVE_DATABASE);
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

            // Bootstrap should come after overwriting configs so they're the same for bootstrap and normal operation
            UnityBootstrap.bootstrap(System.getProperty("rpcPort"));

            this.localNode = node;
        }
        else {
            throw new IllegalStateException("Attempted to start running a local node but one is already running!");
        }
    }

    /**
     * Stops a local node if one is currently running.
     */
    public void shutdownLocalNode() {
        if (this.localNode != null) {
            try {
                this.localNode.blockingStop(EXIT_LOCK_TIMEOUT, EXIT_LOCK_TIMEOUT_UNIT);
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
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
            return NodeListener.listenTo(this.localNode.getID());
        } else {
            throw new IllegalStateException("Attempted to get a new listener but no local node is currently running!");
        }
    }

    private void checkKernelExistsAndOverwriteConfigs() throws IOException {
        if (!kernelExists()) {
            throw new TestRunnerInitializationException("Expected to find a kernel at: " + expectedKernelLocation);
        }
        overwriteConfigAndGenesis();
    }

    private void setRpcPort(String port) throws IOException {
        Charset charset = StandardCharsets.UTF_8;

        if (nodeType == NodeType.RUST_NODE) {
            Path path = Paths.get(expectedKernelLocation + "/custom/custom.toml");
            String content = new String(Files.readAllBytes(path), charset);
            content = content.replaceFirst("port = 8545", "port = " + port);
            content = content.replaceFirst("port = 8546", "port = 2" + port);
            content = content.replaceFirst("local_node = \"p2p://00000000-0000-0000-0000-000000000000@0\\.0\\.0\\.0:30303\"", "local_node = \"p2p://00000000-0000-0000-0000-000000000000@0.0.0.0:3" + port + "\"");
            Files.write(path, content.getBytes(charset));
        } else if (nodeType == NodeType.JAVA_NODE) {
            Path path = Paths.get(expectedKernelLocation + "/custom/config/config.xml");
            String content = new String(Files.readAllBytes(path), charset);
            content = content.replaceFirst("<rpc active=\"true\" ip=\"127\\.0\\.0\\.1\" port=\"\\d{4}\">", "<rpc active=\"true\" ip=\"127.0.0.1\" port=\"" + port + "\">");
            content = content.replaceFirst("<java active=\"true\" ip=\"127\\.0\\.0\\.1\" port=\"\\d{4}\">", "<java active=\"true\" ip=\"127.0.0.1\" port=\"2" + port + "\">");
            content = content.replaceFirst("<port>\\d{5}</port>", "<port>3" + port + "</port>");
            Files.write(path, content.getBytes(charset));
        } else {
            throw new IllegalArgumentException("Unknown Node Type");
        }
    }

    private boolean kernelExists() {
        File kernel = new File(expectedKernelLocation);
        return kernel.exists() && kernel.isDirectory();
    }

    private void overwriteConfigAndGenesis() throws IOException {
        if(nodeType == NodeType.RUST_NODE) {
            overwriteIfTargetDirExists(new File(handedwrittenConfigs),
                new File(expectedKernelLocation + "/custom"));
            FileUtils.copyFile(new File (handedwrittenConfigs + "/env"), new File(expectedKernelLocation + "/env"));
        } else if(nodeType == NodeType.JAVA_NODE || nodeType == NodeType.PROXY_JAVA_NODE ) {
            FileUtils.copyFile(new File(handedwrittenConfigs + "/fork.properties"), new File(expectedKernelLocation + "/networks/custom/fork.properties"));
            FileUtils.copyFile(new File(handedwrittenConfigs + "/genesis.json"), new File(expectedKernelLocation + "/networks/custom/genesis.json"));
            FileUtils.copyFile(new File(handedwrittenConfigs + "/config.xml"), new File(expectedKernelLocation + "/custom/config/config.xml"));
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
