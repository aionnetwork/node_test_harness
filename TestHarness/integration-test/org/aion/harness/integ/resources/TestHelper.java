package org.aion.harness.integ.resources;

import java.io.File;
import java.io.IOException;
import org.aion.harness.main.LocalNode;
import org.aion.harness.main.Network;
import org.aion.harness.main.NodeConfigurations;
import org.aion.harness.main.NodeConfigurations.DatabaseOption;
import org.aion.harness.main.NodeFactory;
import org.apache.commons.io.FileUtils;

public class TestHelper {
    // Note that the normal install location for the kernel is in Tests, so use that one.
    private static final String WORKING_DIR = System.getProperty("user.dir");
    public static final File EXPECTED_KERNEL_ROOT = getDeepChild(new File(WORKING_DIR).getParentFile(), "Tests", "oan");
    public static final Network DEFAULT_NETWORK = Network.CUSTOM;

    public static File getDefaultDatabaseLocation() {
        return getDeepChild(EXPECTED_KERNEL_ROOT, DEFAULT_NETWORK.string(), "database");
    }

    public static File getDatabaseLocationByNetwork(Network network) {
        return getDeepChild(EXPECTED_KERNEL_ROOT, network.string(), "database");
    }

    public static LocalNode configureDefaultLocalNodeAndDoNotPreserveDatabase() throws IOException {
        return getDefaultLocalNode(DEFAULT_NETWORK, DatabaseOption.DO_NOT_PRESERVE_DATABASE);
    }

    public static LocalNode configureDefaultLocalNodeToPreserveDatabase() throws IOException {
        return getDefaultLocalNode(DEFAULT_NETWORK, DatabaseOption.PRESERVE_DATABASE);
    }

    public static LocalNode configureDefaultLocalNodeToPreserveDatabaseForNetwork(Network network)
            throws IOException {
        return getDefaultLocalNode(network, DatabaseOption.PRESERVE_DATABASE);
    }

    public static LocalNode configureDefaultLocalNodeForNetwork(Network network)
            throws IOException {
        return getDefaultLocalNode(network, DatabaseOption.DO_NOT_PRESERVE_DATABASE);
    }

    private static LocalNode getDefaultLocalNode(Network network, DatabaseOption databaseOption)
            throws IOException {
        if (!EXPECTED_KERNEL_ROOT.exists()) {
            System.err.println(
                    "-------------------------------------------------------------------------------------------");
            System.err.println(
                    "ERROR: This test expects there to be an already built kernel for it to run against!");
            System.err.println(
                    "The built kernel is expected to be found at the following location: "
                            + EXPECTED_KERNEL_ROOT);
            System.err.println(
                    "-------------------------------------------------------------------------------------------");
            throw new IOException("Failed to find expected built kernel for test!");
        }

        if (!EXPECTED_KERNEL_ROOT.isDirectory()) {
            System.err.println(
                    "-------------------------------------------------------------------------------------------");
            System.err.println(
                    "ERROR: This test expects there to be an already built kernel for it to run against!");
            System.err.println(
                    "A file was found at the expected location but it is not a directory: "
                            + EXPECTED_KERNEL_ROOT);
            System.err.println("This must be the root directory of the built kernel.");
            System.err.println(
                    "-------------------------------------------------------------------------------------------");
            throw new IOException("Failed to find expected built kernel for test!");
        }

        // Overwrites the config, fork and genesis files of each network.
        FileUtils.copyDirectory(new File(WORKING_DIR + "/resources/config"), getDeepChild(EXPECTED_KERNEL_ROOT, "config"));
        overwriteIfTargetDirExists(new File(WORKING_DIR + "/resources/config/custom"), getDeepChild(EXPECTED_KERNEL_ROOT, "custom", "config"));
        overwriteIfTargetDirExists(new File(WORKING_DIR + "/resources/config/mainnet"), getDeepChild(EXPECTED_KERNEL_ROOT, "mainnet", "config"));
        overwriteIfTargetDirExists(new File(WORKING_DIR + "/resources/config/amity"), getDeepChild(EXPECTED_KERNEL_ROOT, "amity", "config"));

        LocalNode node = NodeFactory.getNewLocalNodeInstance(NodeFactory.NodeType.JAVA_NODE);
        node.configure(
                NodeConfigurations.alwaysUseBuiltKernel(
                        network, EXPECTED_KERNEL_ROOT.getAbsolutePath(), databaseOption));
        return node;
    }

    private static void overwriteIfTargetDirExists(File source, File target) throws IOException {
        if (target.exists()) {
            FileUtils.copyDirectory(source, target);
        }
    }

    private static File getDeepChild(File parent, String... fragments) {
        File result = parent;
        for (String fragment : fragments) {
            result = new File(result, fragment);
        }
        return result;
    }
}
