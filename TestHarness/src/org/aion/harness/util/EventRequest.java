package org.aion.harness.util;

import java.util.concurrent.TimeUnit;
import org.aion.harness.main.event.IEvent;
import org.aion.harness.result.FutureResult;
import org.aion.harness.result.LogEventResult;

/**
 * An event request is an internal object maintained by the {@link LogListener} so that it can
 * essentially hold onto the event it is listening for, the state of that event, and its future.
 *
 * This class should not be exposed to any other classes.
 *
 * An event request is mutable while its current state is PENDING. It is called "finalized" when it
 * moves into any non-PENDING state. Once it is moved into a non-PENDING state, the object becomes
 * entirely immutable.
 *
 * This class is partially thread-safe. Read method documentation carefully.
 */
public final class EventRequest {
    private static long instanceCount = 0;
    private final long ID;

    public final FutureResult<LogEventResult> future = new FutureResult<>();
    private final IEvent requestedEvent;
    private final long deadlineInNanos;

    private enum RequestState { PENDING, SATISFIED, UNOBSERVED, REJECTED, EXPIRED }

    private RequestState currentState = RequestState.PENDING;
    private String causeOfRejection;
    private long timeOfObservationInNanos = -1;

    /**
     * Constructs a new event request for the specified event.
     *
     * @param eventToRequest The event to request to be listened for.
     * @param deadline The time at which this request expires.
     * @param unit The unit of time of the deadline.
     */
    public EventRequest(IEvent eventToRequest, long deadline, TimeUnit unit) {
        this.requestedEvent = eventToRequest;
        this.deadlineInNanos = unit.toNanos(deadline);
        this.ID = instanceCount++;
    }

    /**
     * This must be called by the {@link LogListener} thread <b>after</b> moving this request into
     * a finalized state.
     *
     * Once this method is called, the future (which is handed off to the event submitting thread)
     * will be consumable.
     *
     * Not thread-safe.
     */
    private void finishFuture() {
        this.future.finish(extractResultFromRequest());
    }

    /**
     * Only to be used by {@link LogListener} to determine whether or not the request is satisfied
     * by a log line.
     *
     * Once this method returns {@code true} once, it will always return {@code true} after that.
     *
     * If the request is already finalized, this method will return {@code true} without doing any
     * work.
     *
     * Not thread-safe.
     *
     * @param line The log line to test.
     * @param currentTime The current time.
     * @param unit The unit of time of the currentTime.
     * @return whether or not this request is satisfied.
     */
    public boolean isSatisfiedBy(String line, long currentTime, TimeUnit unit) {
        markAsExpiredIfPastDeadline(currentTime, unit);

        if (this.currentState != RequestState.PENDING) {
            return true;
        }

        boolean isSatisfied = this.requestedEvent.isSatisfiedBy(line, currentTime, unit);

        if (isSatisfied) {
            this.currentState = RequestState.SATISFIED;
            this.timeOfObservationInNanos = unit.toNanos(currentTime);
            finishFuture();
        }

        return isSatisfied;
    }

    /**
     * Returns {@code true} only if this request is expired at the given time.
     *
     * Thread safe.
     *
     * @param time The time to test.
     * @param unit The unit of time that 'time' is in.
     * @return whether or not this request is expired.
     */
    public boolean isExpiredAtTime(long time, TimeUnit unit) {
        return unit.toNanos(time) > this.deadlineInNanos;
    }

    /**
     * Finalizes this request by moving it into the REJECTED state only if it is not already
     * finalized.
     *
     * Thread safe.
     *
     * @param cause The reason for rejecting the event.
     */
    public synchronized void markAsRejected(String cause) {
        if (this.currentState == RequestState.PENDING) {
            this.causeOfRejection = cause;
            this.currentState = RequestState.REJECTED;
            finishFuture();
        }
    }

    /**
     * Finalizes this request by moving it into the UNOBSERVED state only if it is not already
     * finalized.
     *
     * Thread safe.
     */
    public synchronized void markAsUnobserved() {
        if (this.currentState == RequestState.PENDING) {
            this.currentState = RequestState.UNOBSERVED;
            finishFuture();
        }
    }

    /**
     * Finalizes this request by moving it into the EXPIRED state only if it is not already
     * finalized.
     *
     * Thread safe.
     */
    public synchronized void markAsExpired() {
        if (this.currentState == RequestState.PENDING) {
            this.currentState = RequestState.EXPIRED;
            finishFuture();
        }
    }

    /**
     * Returns {@code true} only if this request is still pending and therefore not finalized.
     *
     * Thread safe.
     *
     * @return whether or not it is still pending.
     */
    public synchronized boolean isPending() {
        return this.currentState == RequestState.PENDING;
    }

    /**
     * Finalizes this request and moves it into the EXPIRED state only if the following conditions
     * are all true:
     *   1. The request is expired at the specified time.
     *   2. The request is not already finalized.
     *
     * @param time The time to test.
     * @param unit The unit of time that 'time' is in.
     */
    private void markAsExpiredIfPastDeadline(long time, TimeUnit unit) {
        if (isExpiredAtTime(time, unit)) {
            markAsExpired();
        }
    }

    private LogEventResult extractResultFromRequest() {
        if (isPending()) {
            throw new IllegalStateException("Cannot extract result from a still-pending request.");
        }

        if (this.currentState == RequestState.SATISFIED) {

            return LogEventResult.observedEvent(
                this.requestedEvent.getAllObservedEvents(),
                this.requestedEvent.getAllObservedLogs(),
                this.timeOfObservationInNanos,
                TimeUnit.NANOSECONDS);

        } else if (this.currentState == RequestState.REJECTED) {

            return LogEventResult.rejectedEvent(
                this.causeOfRejection,
                this.requestedEvent.getAllObservedEvents(),
                this.requestedEvent.getAllObservedLogs());

        } else if (this.currentState == RequestState.EXPIRED) {

            return LogEventResult.expiredEvent(
                this.requestedEvent.getAllObservedEvents(),
                this.requestedEvent.getAllObservedLogs());

        } else {

            return LogEventResult.unobservedEvent(
                this.requestedEvent.getAllObservedEvents(),
                this.requestedEvent.getAllObservedLogs());

        }
    }

    @Override
    public synchronized String toString() {
        return "EventRequest { event request = " + this.requestedEvent + ", event state = " + this.currentState + " }";
    }

    /**
     * An event request is only equal to itself and no other event request object.
     *
     * @implNote This is based solely off a unique identifier, which is a {@code long}, and thus
     * this object can only be equal to another event request if the entire long value range has
     * been used up.
     *
     * @param other The other object whose equality is to be tested.
     * @return whether or not this object is equal to other.
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof EventRequest)) {
            return false;
        }

        EventRequest otherEventRequest = (EventRequest) other;
        return this.ID == otherEventRequest.ID;
    }

    @Override
    public int hashCode() {
        return (int) this.ID;
    }

}
