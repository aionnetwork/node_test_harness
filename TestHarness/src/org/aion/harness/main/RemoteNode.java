package org.aion.harness.main;

import org.aion.harness.result.Result;

import java.io.File;
import java.io.IOException;

public interface RemoteNode {

    /**
     * Returns the unique identity of this node.
     *
     * @return the node's identity.
     */
    public int getID();

    /**
     * Given the log file, connect to the remote node.
     *
     * @param logFile of the remote node
     * @return connection result
     */
    public Result connect(File logFile) throws IOException;

    /**
     * Disconnect the remote node.
     *
     * @return disconnect result
     */
    public Result disconnect() throws InterruptedException;
}
