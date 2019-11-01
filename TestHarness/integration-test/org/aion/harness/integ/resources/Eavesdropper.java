package org.aion.harness.integ.resources;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.aion.harness.main.event.Event;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.main.NodeListener;
import org.aion.harness.main.event.JavaPrepackagedLogEvents;
import org.aion.harness.result.LogEventResult;

public final class Eavesdropper implements Runnable {
    private NodeListener listener;
    private Gossip gossip;
    private int ID;
    private AtomicBoolean dead = new AtomicBoolean(false);

    private AtomicBoolean freezeResult = new AtomicBoolean(false);
    private LogEventResult latestResult = null;

    public enum Gossip {

        // Listen for the node's heartbeat.
        HEARTBEAT,

        // Listen for an event that will never occur.
        UNSPEAKABLE

    }

    private Eavesdropper(Gossip gossip, NodeListener listener, int ID) {
        this.listener = listener;
        this.gossip = gossip;
        this.ID = ID;
    }

    public static Eavesdropper createEavesdropperThatListensFor(Gossip gossip, int ID, int nodeId) {
        return new Eavesdropper(gossip, NodeListener.listenTo(nodeId), ID);
    }

    @Override
    public void run() {
        IEvent event = new Event("I do not exist.");

        while (!this.dead.get()) {

            long startTimeInNanos;
            LogEventResult result = null;

            if (this.gossip == Gossip.HEARTBEAT) {

                // Listen for a regular heartbeat event.
                startTimeInNanos = System.nanoTime();

                try {
                    // Eavesdropper is currently used by tests of TestHarness in a way that
                    // doesn't care what these events actually say.  Hardcoding the Java messages
                    // for now, which is good enough for the tests to work.
                    IEvent heartbeat = new JavaPrepackagedLogEvents().getHeartbeatEvent();
                    result = this.listener.listenForEvent(heartbeat, 2, TimeUnit.MINUTES).get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // If we die during the waitFor it is possible for it to return null.
                if ((result == null) && (!this.dead.get())) {
                    throw new IllegalStateException("waitFor returned null and we are still alive!");
                } else if (result == null) {
                    break;
                }

                if (result.eventWasObserved()) {
                    long time = TimeUnit.NANOSECONDS.toSeconds(result.timeOfObservation(TimeUnit.NANOSECONDS) - startTimeInNanos);
                    System.out.println("Thread #" + this.ID + ": event observed | time: " + time + " second(s)");
                } else if (result.eventWasRejected()) {
                    System.out.println("Thread #" + this.ID + ": event request rejected due to: " + result.causeOfRejection());
                } else {
                    System.out.println("Thread #" + this.ID + ": event unobserved.");
                }

            } else if (this.gossip == Gossip.UNSPEAKABLE) {

                // Listen for an event that will never occur.
                try {
                    result = this.listener.listenForEvent(event, 1, TimeUnit.HOURS).get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

            }

            if (!this.dead.get()) {
                if ((this.latestResult == null) || (!this.freezeResult.get())) {
                    synchronized (this) {
                        this.latestResult = result;
                    }
                }
            }

        }
    }

    public void kill() {
        this.dead.set(true);
    }

    public boolean isAlive() {
        return !this.dead.get();
    }

    /**
     * The eavesdropper will not update the latest result anymore, unless it is currently null.
     *
     * Therefore if there is no latest result yet, once the first result comes in it will get frozen
     * and not be updated. If there is a result, then it will just get frozen.
     */
    public void freezeLatestResult() {
        this.freezeResult.set(true);
    }

    /**
     * The eavesdropper will update the latest result every time a new result is produced.
     */
    public void unfreezeLatestResult() {
        this.freezeResult.set(false);
    }

    public synchronized LogEventResult fetchLatestResult() {
        return this.latestResult;
    }

}
