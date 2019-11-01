package org.aion.harness.main;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.global.SingletonFactory;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;
import org.aion.harness.util.LogListener;

/**
 * A listener that listens to a node and waits for logging events to occur.
 *
 * If no node is currently alive then all {@code waitFor...} methods in this class return immediately
 * with rejected results.
 *
 * All methods that begin with {@code waitFor} are blocking methods that will not return until the
 * event has been moved into some final state: either it was observed, unobserved (meaning the node
 * shut down before it was seen), it expired (timed out) or it was rejected (for various reasons).
 *
 * This class is not thread-safe.
 */
public final class NodeListener {
    private final LogListener logListener;

    private NodeListener(LogListener logListener) {
        this.logListener = logListener;
    }

    public static NodeListener listenTo(int nodeId) {
        return new NodeListener(SingletonFactory.singleton().nodeWatcher().getReaderForNodeByID(nodeId).getLogListener());
    }

    /**
     * Listens for the specified events.
     *
     * This method is non-blocking but returns blocking {@link java.util.concurrent.Future}
     * implementations.
     *
     * All events being listened for will have the exact same timeout values, which are the
     * values specified in the input parameters.
     *
     * @param events The events.
     * @param timeout The duration after which the events expire.
     * @param unit The time unit of the duration.
     * @return the results of the these events.
     */
    public List<FutureResult<LogEventResult>> listenForEvents(List<IEvent> events, long timeout, TimeUnit unit) {
        List<FutureResult<LogEventResult>> futures = new ArrayList<>();

        for (IEvent event : events) {
            futures.add(listenForEvent(event, timeout, unit));
        }

        return futures;
    }

    /**
     * Listens for the specified event to occur.
     *
     * This method is non-blocking but returns a blocking {@link java.util.concurrent.Future}
     * implementation.
     *
     * @param event The event to listen for.
     * @param timeout The duration after which the event expires.
     * @param unit The time unit of the duration.
     * @return the result of this event.
     */
    public FutureResult<LogEventResult> listenForEvent(IEvent event, long timeout, TimeUnit unit) {
        if (event == null) {
            throw new NullPointerException("Cannot wait for a null event.");
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("Timeout value was negative: " + timeout);
        }
        if (unit == null) {
            throw new IllegalArgumentException("Cannot specify a null time unit.");
        }

        return this.logListener.submitEventToBeListenedFor(event, timeout, unit);
    }

    /**
     * Returns the number of events that are currently being listened for. These events may have
     * been requested by separate {@link NodeListener} objects. But these are the total number
     * currently being processed.
     *
     * @return total number of events being listened for.
     */
    public int numberOfEventsBeingListenedFor() {
        return this.logListener.numberOfPendingEventRequests();
    }

}
