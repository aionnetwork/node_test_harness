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
    private static final String WORKING_DIR = System.getProperty("user.dir");
    public static final String EXPECTED_BUILD_LOCATION = WORKING_DIR + File.separator + "aion";
    public static final Network DEFAULT_NETWORK = Network.CUSTOM;

    public static File getDefaultDatabaseLocation() {
        return new File(
                EXPECTED_BUILD_LOCATION
                        + File.separator
                        + DEFAULT_NETWORK.string()
                        + File.separator
                        + "database");
    }

    public static File getDatabaseLocationByNetwork(Network network) {
        return new File(
                EXPECTED_BUILD_LOCATION
                        + File.separator
                        + network.string()
                        + File.separator
                        + "database");
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
        File expectedBuildLocation = new File(EXPECTED_BUILD_LOCATION);

        if (!expectedBuildLocation.exists()) {
            System.err.println(
                    "-------------------------------------------------------------------------------------------");
            System.err.println(
                    "ERROR: This test expects there to be an already built kernel for it to run against!");
            System.err.println(
                    "The built kernel is expected to be found at the following location: "
                            + EXPECTED_BUILD_LOCATION);
            System.err.println(
                    "-------------------------------------------------------------------------------------------");
            throw new IOException("Failed to find expected built kernel for test!");
        }

        if (!expectedBuildLocation.isDirectory()) {
            System.err.println(
                    "-------------------------------------------------------------------------------------------");
            System.err.println(
                    "ERROR: This test expects there to be an already built kernel for it to run against!");
            System.err.println(
                    "A file was found at the expected location but it is not a directory: "
                            + EXPECTED_BUILD_LOCATION);
            System.err.println("This must be the root directory of the built kernel.");
            System.err.println(
                    "-------------------------------------------------------------------------------------------");
            throw new IOException("Failed to find expected built kernel for test!");
        }

        // Overwrites the config, fork and genesis files of each network.
        FileUtils.copyDirectory(new File(WORKING_DIR + "/resources/config"), new File(EXPECTED_BUILD_LOCATION + "/config"));
        overwriteIfTargetDirExists(new File(WORKING_DIR + "/resources/config/avmtestnet"), new File(EXPECTED_BUILD_LOCATION + "/avmtestnet/config"));
        overwriteIfTargetDirExists(new File(WORKING_DIR + "/resources/config/conquest"), new File(EXPECTED_BUILD_LOCATION + "/conquest/config"));
        overwriteIfTargetDirExists(new File(WORKING_DIR + "/resources/config/custom"), new File(EXPECTED_BUILD_LOCATION + "/custom/config"));
        overwriteIfTargetDirExists(new File(WORKING_DIR + "/resources/config/mastery"), new File(EXPECTED_BUILD_LOCATION + "/mastery/config"));
        overwriteIfTargetDirExists(new File(WORKING_DIR + "/resources/config/mainnet"), new File(EXPECTED_BUILD_LOCATION + "/mainnet/config"));

        LocalNode node = NodeFactory.getNewLocalNodeInstance(NodeFactory.NodeType.JAVA_NODE);
        node.configure(
                NodeConfigurations.alwaysUseBuiltKernel(
                        network, EXPECTED_BUILD_LOCATION, databaseOption));
        return node;
    }

    private static void overwriteIfTargetDirExists(File source, File target) throws IOException {
        if (target.exists()) {
            FileUtils.copyDirectory(source, target);
        }
    }
}
