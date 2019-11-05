package org.aion.harness.main.impl;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aion.harness.main.LocalNode;
import org.aion.harness.main.Network;
import org.aion.harness.main.NodeConfigurations;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.event.Event;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.global.SingletonFactory;
import org.aion.harness.misc.Assumptions;
import org.aion.harness.result.Result;
import org.aion.harness.sys.RustLeveldbLockAwaiter;
import org.aion.harness.util.LogManager;
import org.aion.harness.util.LogReader;
import org.aion.harness.util.NodeFileManager;
import org.aion.harness.util.SimpleLog;
import org.apache.commons.io.FileUtils;

public class RustNode implements LocalNode {
    private final SimpleLog log;
    private NodeConfigurations configurations;
    private LogReader logReader;
    private LogManager logManager;
    private final int ID;
    private boolean isInitialized;

    // The running instance of the kernel.
    private Process runningKernel = null;

    /** The directory name of the database (relative to path of aionr root).  */
    private static final String DATA_DIR = "data";

    public RustNode() {
        this.log = new SimpleLog(getClass().getName());
        this.configurations = null;
        this.logReader = new LogReader();
        this.logManager = new LogManager();
        this.ID = SingletonFactory.singleton().nodeWatcher().addReader(this.logReader);
        this.isInitialized = false;
    }

    @Override
    public void configure(NodeConfigurations nc) {
        if (isAlive()) {
            throw new IllegalStateException("Cannot set a node's configurations while it is running.");
        }

        if (nc == null) {
            throw new NullPointerException("Cannot configure node with null configurations.");
        }

        // didn't implement these for now because I didn't need them for any Rust tests so far.
        if(nc.alwaysBuildFromSource()) {
            throw new UnsupportedOperationException("RustNode only supports prebuilt kernel");
        }
        if (!nc.preserveDatabase()) {
            try {
                FileUtils.deleteDirectory(new File(nc.getDirectoryOfBuiltKernel().getPath() + "/" + DATA_DIR));
            } catch (IOException e) {
                throw new RuntimeException("Couldn't clear Rust Database");
            }
        }

        this.configurations = nc;
    }

    @Override
    public Network getNetwork() {
        return (this.configurations == null) ? null : this.configurations.getNetwork();
    }

    @Override
    public Result initialize() throws IOException {
        Result result = useExistingBuild();

        // If initialization was successful then set up the log files.
        if (result.isSuccess()) {
            result = this.logManager.setupLogFiles();
            this.isInitialized = true;
        }

        return result;

    }

    private Result useExistingBuild() {
        // lifted from NodeInitializer#useExistingBuild
        if (!this.configurations.getDirectoryOfBuiltKernel().exists()) {
            return Result.unsuccessfulDueTo(
                "Could not find built kernel directory at location: " +
                    this.configurations.getDirectoryOfBuiltKernel().getAbsolutePath());
        }
        if (!this.configurations.getDirectoryOfBuiltKernel().isDirectory()) {
            return Result.unsuccessfulDueTo(
                "Found the built kernel, but it is not a directory! Path: " +
                    this.configurations.getDirectoryOfBuiltKernel().getAbsolutePath());
        }

        return Result.successful();
    }

    /**
     * {@inheritDoc}
     *
     * In RustNode, {@link #initializeVerbose()} is exactly the same as {@link #initialize()}.
     */
    @Override
    public Result initializeVerbose() throws IOException {
        // initializeVerbose only has an effect if using the build-from-source
        // NodeConfiguration.  RustNode does not support build-from-source, so
        // just initialize normally.
        return initialize();
    }

    @Override
    public Result start() throws IOException, InterruptedException {
        if (this.configurations == null) {
            throw new IllegalStateException("Node has not been configured yet! Cannot start kernel.");
        }
        if (isAlive()) {
            throw new IllegalStateException("There is already a kernel running.");
        }
        if (!this.isInitialized) {
            throw new IllegalStateException("This node has not been initialized yet!");
        }

        log.log("Starting Rust kernel node...");

        final String cfgFile;
        switch(configurations.getNetwork()) {
            case CUSTOM:
                cfgFile = "custom/custom.toml";
                break;
            case MAINNET:
                cfgFile = "mainnet/mainnet.toml";
                break;
            case MASTERY:
                cfgFile = "mastery/mastery.toml";
                break;
            default:
                throw new IllegalArgumentException("Unsupported network");
        }

        log.log("start network: " + cfgFile);

        String ldLib = System.getProperty("java.home") + File.separator + "lib" + File.separator + "server"
            + ":"
            + configurations.getDirectoryOfBuiltKernel() + File.separator + "libs";

        ProcessBuilder builder = new ProcessBuilder("./aion",
            String.format("--config=%s", cfgFile),
            "-d", DATA_DIR,
            // --author arg needed for aionr miner, but its value is not depended on by tests
            "--author", "a0d6dec327f522f9c8d342921148a6c42f40a3ce45c1f56baa7bfa752200d9e5"
        ).directory(this.configurations.getActualBuildLocation());
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        builder.environment().put("LD_LIBRARY_PATH", ldLib);
        builder.environment().put("AIONR_HOME", ".");

        builder.redirectErrorStream(true);
        builder.redirectOutput(this.logManager.getCurrentOutputLogFile());

        File levelDbBaseDir = configurations.getDatabaseRust(DATA_DIR);
        // if null, don't need to wait because the db doesn't exist yet
        if(levelDbBaseDir != null) {
            new RustLeveldbLockAwaiter(levelDbBaseDir.getAbsolutePath()).await();
        }

        this.runningKernel = builder.start();
        return waitForReadyOrError(this.logManager.getCurrentOutputLogFile());
    }

    @Override
    public Result stop() throws IOException, InterruptedException {

        Result result;

        if (isAlive()) {
            log.log(Assumptions.LOGGER_BANNER + "Stopping Rust kernel node...");

            this.runningKernel.destroy();
            boolean shutdown = this.runningKernel.waitFor(1, TimeUnit.MINUTES);
            this.runningKernel = null;
            this.logReader.stopReading();

            result = (shutdown) ? Result.successful() : Result.unsuccessfulDueTo("Timed out waiting for node to shut down!");

            log.log(Assumptions.LOGGER_BANNER + "Rust kernel node stopped.");

        } else {
            result = Result.unsuccessfulDueTo("Node is not currently alive!");
        }

        if (this.isInitialized) {
            // Finds the kernel and kills it (above we are killing the aion.sh script,
            // which is not guaranteed to kill the kernel). We find these processes because we know the
            // directory of the executable, so we can hunt it down precisely.
            String executableDir = NodeFileManager.getExecutableDirectoryOf(this.configurations.getActualBuildLocation());
            ProcessHandle.allProcesses()
                .filter(process -> process.info().command().toString().contains(executableDir))
                .forEach(kernel -> kernel.destroy());
        }

        return result;
    }

    @Override
    public Result blockingStop(long timeout, TimeUnit timeoutUnit) throws IOException, InterruptedException {
        Result res = stop();
        if(! res.isSuccess()) {
            return res;
        }

        File levelDbBaseDir = configurations.getDatabaseRust(DATA_DIR);
        final boolean leveldbLockReleased;
        // if null, don't need to wait because the db doesn't exist yet
        if(levelDbBaseDir != null) {
            leveldbLockReleased = new RustLeveldbLockAwaiter(
                levelDbBaseDir.getAbsolutePath()).await(timeout, timeoutUnit);
        } else {
            leveldbLockReleased = true;
        }

        return leveldbLockReleased? Result.successful() : Result.unsuccessfulDueTo(
            "Leveldb lock used by node was not released after node termination");
    }

    @Override
    public boolean isAlive() {
        return this.runningKernel != null && this.runningKernel.isAlive();
    }

    @Override
    public Result resetState() throws IOException {
        if (this.configurations == null) {
            throw new IllegalStateException("Node has not been configured yet! Cannot reset kernel state.");
        }
        if (isAlive()){
            throw new IllegalStateException("Cannot reset state while the node is running.");
        }
        if (!this.isInitialized) {
            throw new IllegalStateException("Node has not been initialized yet!");
        }

        log.log("Resetting the state of the Rust kernel node...");

        File database = new File(configurations.getDirectoryOfBuiltKernel()
            + File.separator + DATA_DIR);
        if (database.exists()) {
            log.log("Removing aionr data dir: " + database.getAbsolutePath());
            FileUtils.deleteDirectory(database);
        }
        return Result.successful();
    }

    @Override
    public int getID() {
        return ID;
    }

    /**
     * Block until logs indicate that either RPC server started or an error happened
     */
    private Result waitForReadyOrError(File outputLog) throws InterruptedException {
        // We wait for the rpc event to know we are ok to return. There is a chance that we will miss
        // this event and start listening too late. That is why we timeout after 20 seconds, which
        // should be more than sufficient for the server to activate, and then we check if the node
        // is still live.
        // See issue #1 relating to this decision, which will be refactored in the future.

        if (isAlive()) {
            // This isn't technically the 'RPC enabled' message because Rust kernel doesn't emit
            // such a log message.  However it seems like it doesn't print this message until
            // the RPC is started (has worked reliably so far).
            IEvent rpcEvent = new Event("External blocks added");

            Result result = this.logReader.startReading(outputLog);
            if (!result.isSuccess()) {
                return result;
            }

            try {
                NodeListener.listenTo(this.getID())
                    .listenForEvent(rpcEvent, 20, TimeUnit.SECONDS)
                    .get(40, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                String msg = "RPC Server did not start within the allotted time (check kernel logs for details)";
                log.log(msg);
                return Result.unsuccessfulDueTo(msg);
            }

            return Result.successful();
        } else {
            log.log("process not alive: " + runningKernel.exitValue() );
            return Result.unsuccessfulDueTo("Node failed to start!");
        }
    }
}
