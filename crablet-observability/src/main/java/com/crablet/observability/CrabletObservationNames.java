package com.crablet.observability;

/**
 * Observation names shared by Crablet modules.
 */
public final class CrabletObservationNames {

    public static final String EVENTSTORE_APPEND = "crablet.eventstore.append";
    public static final String EVENTSTORE_CONCURRENCY_VIOLATION = "crablet.eventstore.concurrency.violation";
    public static final String EVENTSTORE_EVENT_TYPE = "crablet.eventstore.event.type";
    public static final String COMMAND_HANDLE = "crablet.command.handle";
    public static final String COMMAND_IDEMPOTENT_DUPLICATE = "crablet.command.idempotent.duplicate";
    public static final String POLLER_LEADERSHIP = "crablet.poller.leadership";
    public static final String POLLER_PROCESSING_CYCLE = "crablet.poller.processing.cycle";
    public static final String POLLER_BACKOFF = "crablet.poller.backoff";
    public static final String VIEW_PROJECT = "crablet.view.project";
    public static final String AUTOMATION_DECIDE = "crablet.automation.decide";
    public static final String OUTBOX_PUBLISH = "crablet.outbox.publish";
    public static final String OUTBOX_PROCESSING_CYCLE = "crablet.outbox.processing.cycle";

    private CrabletObservationNames() {
    }
}
