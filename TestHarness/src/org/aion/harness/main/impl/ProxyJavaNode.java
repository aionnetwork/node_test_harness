package org.aion.harness.main.impl;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.event.AndEvent;
import org.aion.harness.main.event.Event;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.result.Result;
import org.aion.harness.util.SimpleLog;

public class ProxyJavaNode extends JavaNode {
    private final SimpleLog log;

    public ProxyJavaNode() {
        super();
        log = new SimpleLog(getClass().getName());
    }

    /**
     * Block until logs indicate that either RPC server started or an error happened
     */
    protected Result waitForKernelReadyOrError(File outputLog) throws InterruptedException {
        if (isAlive()) {
            Result result = this.logReader.startReading(outputLog);
            if (!result.isSuccess()) {
                return result;
            }

            IEvent rpcEv = new Event("rpc-server - (UNDERTOW) started");
            IEvent havePeerEv = new Event("outbound -> active");
            IEvent nearBestEv = new Event("PendingStateImpl.processBest: closeToNetworkBest[true]");

            try {
                NodeListener listener = NodeListener.listenTo(this.getID());

                FutureResult<LogEventResult> futureNearBestBlock = listener
                    .listenForEvent(nearBestEv, 5, TimeUnit.MINUTES);

                listener.listenForEvent(
                    rpcEv.and(havePeerEv), 60, TimeUnit.SECONDS
                ).get(60, TimeUnit.SECONDS);

                log.log("Kernel RPC server started and peer found.  Waiting for sync to get near best network block.");
                futureNearBestBlock.get(5, TimeUnit.MINUTES);
                log.log("Kernel is near best block.");
            } catch (TimeoutException te) {
                String msg = "Kernel start-up timeout.  Either the kernel RPC server didnt start, "
                    + "or it was not able to sync to network best within 5 minutes.";
                log.log(msg);
                try {
                    stop();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    log.log("Failed to stop kernel after start-up checks failed.");
                }
                return Result.unsuccessfulDueTo(msg);
            }

            return Result.successful();
        } else {
            return Result.unsuccessfulDueTo("Node failed to start!");
        }
    }
}
