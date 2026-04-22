package com.crablet.metrics.micrometer;

/**
 * Metric name constants used by {@link MicrometerMetricsCollector}.
 * These names are the Micrometer (dot-separated) form; Prometheus adds underscores and suffixes.
 */
public final class CrabletMetricNames {

    private CrabletMetricNames() {}

    // EventStore
    public static final String EVENTSTORE_EVENTS_APPENDED = "eventstore.events.appended";
    public static final String EVENTSTORE_EVENTS_BY_TYPE = "eventstore.events.by_type";
    public static final String EVENTSTORE_CONCURRENCY_VIOLATIONS = "eventstore.concurrency.violations";

    // Commands
    public static final String COMMANDS_INFLIGHT = "commands.inflight";
    public static final String COMMANDS_DURATION = "eventstore.commands.duration";
    public static final String COMMANDS_TOTAL = "eventstore.commands.total";
    public static final String COMMANDS_FAILED = "eventstore.commands.failed";
    public static final String COMMANDS_IDEMPOTENT = "eventstore.commands.idempotent";

    // Outbox
    public static final String OUTBOX_EVENTS_PUBLISHED = "outbox.events.published";
    public static final String OUTBOX_PUBLISHING_DURATION = "outbox.publishing.duration";
    public static final String OUTBOX_PROCESSING_CYCLES = "outbox.processing.cycles";
    public static final String OUTBOX_ERRORS = "outbox.errors";

    // Poller / processor
    public static final String PROCESSOR_IS_LEADER = "processor.is_leader";
    public static final String POLLER_PROCESSING_CYCLES = "poller.processing.cycles";
    public static final String POLLER_EVENTS_FETCHED = "poller.events.fetched";
    public static final String POLLER_EMPTY_POLLS = "poller.empty.polls";
    public static final String POLLER_BACKOFF_ACTIVE = "poller.backoff.active";
    public static final String POLLER_BACKOFF_EMPTY_POLL_COUNT = "poller.backoff.empty_poll_count";

    // Views
    public static final String VIEWS_PROJECTION_DURATION = "views.projection.duration";
    public static final String VIEWS_EVENTS_PROJECTED = "views.events.projected";
    public static final String VIEWS_PROJECTION_ERRORS = "views.projection.errors";

    // Automations
    public static final String AUTOMATIONS_EXECUTION_DURATION = "automations.execution.duration";
    public static final String AUTOMATIONS_EVENTS_PROCESSED = "automations.events.processed";
    public static final String AUTOMATIONS_EXECUTION_ERRORS = "automations.execution.errors";
}
