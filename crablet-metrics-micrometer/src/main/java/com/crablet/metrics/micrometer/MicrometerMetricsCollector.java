package com.crablet.metrics.micrometer;

import com.crablet.automations.metrics.AutomationExecutionErrorMetric;
import com.crablet.automations.metrics.AutomationExecutionMetric;
import com.crablet.command.metrics.CommandFailureMetric;
import com.crablet.command.metrics.CommandStartedMetric;
import com.crablet.command.metrics.CommandSuccessMetric;
import com.crablet.command.metrics.IdempotentOperationMetric;
import com.crablet.eventpoller.metrics.BackoffStateMetric;
import com.crablet.eventpoller.metrics.LeadershipMetric;
import com.crablet.eventpoller.metrics.ProcessingCycleMetric;
import com.crablet.eventstore.metrics.ConcurrencyViolationMetric;
import com.crablet.eventstore.metrics.EventTypeMetric;
import com.crablet.eventstore.metrics.EventsAppendedMetric;
import com.crablet.outbox.metrics.EventsPublishedMetric;
import com.crablet.outbox.metrics.OutboxErrorMetric;
import com.crablet.outbox.metrics.PublishingDurationMetric;
import com.crablet.views.metrics.ViewProjectionErrorMetric;
import com.crablet.views.metrics.ViewProjectionMetric;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.crablet.metrics.micrometer.CrabletMetricNames.AUTOMATIONS_EVENTS_PROCESSED;
import static com.crablet.metrics.micrometer.CrabletMetricNames.AUTOMATIONS_EXECUTION_DURATION;
import static com.crablet.metrics.micrometer.CrabletMetricNames.AUTOMATIONS_EXECUTION_ERRORS;
import static com.crablet.metrics.micrometer.CrabletMetricNames.COMMANDS_DURATION;
import static com.crablet.metrics.micrometer.CrabletMetricNames.COMMANDS_FAILED;
import static com.crablet.metrics.micrometer.CrabletMetricNames.COMMANDS_IDEMPOTENT;
import static com.crablet.metrics.micrometer.CrabletMetricNames.COMMANDS_INFLIGHT;
import static com.crablet.metrics.micrometer.CrabletMetricNames.COMMANDS_TOTAL;
import static com.crablet.metrics.micrometer.CrabletMetricNames.EVENTSTORE_CONCURRENCY_VIOLATIONS;
import static com.crablet.metrics.micrometer.CrabletMetricNames.EVENTSTORE_EVENTS_APPENDED;
import static com.crablet.metrics.micrometer.CrabletMetricNames.EVENTSTORE_EVENTS_BY_TYPE;
import static com.crablet.metrics.micrometer.CrabletMetricNames.OUTBOX_ERRORS;
import static com.crablet.metrics.micrometer.CrabletMetricNames.OUTBOX_EVENTS_PUBLISHED;
import static com.crablet.metrics.micrometer.CrabletMetricNames.OUTBOX_PROCESSING_CYCLES;
import static com.crablet.metrics.micrometer.CrabletMetricNames.OUTBOX_PUBLISHING_DURATION;
import static com.crablet.metrics.micrometer.CrabletMetricNames.POLLER_BACKOFF_ACTIVE;
import static com.crablet.metrics.micrometer.CrabletMetricNames.POLLER_BACKOFF_EMPTY_POLL_COUNT;
import static com.crablet.metrics.micrometer.CrabletMetricNames.POLLER_EMPTY_POLLS;
import static com.crablet.metrics.micrometer.CrabletMetricNames.POLLER_EVENTS_FETCHED;
import static com.crablet.metrics.micrometer.CrabletMetricNames.POLLER_PROCESSING_CYCLES;
import static com.crablet.metrics.micrometer.CrabletMetricNames.PROCESSOR_IS_LEADER;
import static com.crablet.metrics.micrometer.CrabletMetricNames.VIEWS_EVENTS_PROJECTED;
import static com.crablet.metrics.micrometer.CrabletMetricNames.VIEWS_PROJECTION_DURATION;
import static com.crablet.metrics.micrometer.CrabletMetricNames.VIEWS_PROJECTION_ERRORS;

/**
 * Micrometer metrics collector for Crablet metric events.
 * <p>
 * Auto-configured by {@link MicrometerMetricsAutoConfiguration} when a {@link MeterRegistry}
 * bean is present. Subscribes to metric events published via Spring Events and records them
 * to Micrometer.
 */
public class MicrometerMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(MicrometerMetricsCollector.class);

    private final MeterRegistry registry;

    private final Map<String, AtomicInteger> leadershipState = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> backoffActiveState = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> backoffEmptyPollState = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> inFlightCommands = new ConcurrentHashMap<>();

    public MicrometerMetricsCollector(MeterRegistry registry) {
        this.registry = registry;
        log.info("MicrometerMetricsCollector initialized");
    }

    // --- EventStore ---

    @EventListener
    public void handleEventsAppended(EventsAppendedMetric event) {
        Counter.builder(EVENTSTORE_EVENTS_APPENDED)
            .description("Total number of events appended to store")
            .register(registry)
            .increment(event.count());
    }

    @EventListener
    public void handleEventType(EventTypeMetric event) {
        Counter.builder(EVENTSTORE_EVENTS_BY_TYPE)
            .description("Events appended by type")
            .tag("event_type", event.eventType())
            .register(registry)
            .increment();
    }

    @EventListener
    public void handleConcurrencyViolation(ConcurrencyViolationMetric event) {
        Counter.builder(EVENTSTORE_CONCURRENCY_VIOLATIONS)
            .description("Total number of DCB concurrency violations")
            .register(registry)
            .increment();
    }

    // --- Commands ---

    @EventListener
    public void handleCommandStarted(CommandStartedMetric event) {
        inFlightCommands.computeIfAbsent(event.commandType(), k -> {
            AtomicInteger gauge = new AtomicInteger(0);
            Gauge.builder(COMMANDS_INFLIGHT, gauge, AtomicInteger::get)
                .description("Commands currently executing")
                .tag("command_type", k)
                .register(registry);
            return gauge;
        }).incrementAndGet();
    }

    @EventListener
    public void handleCommandSuccess(CommandSuccessMetric event) {
        inFlightCommands.getOrDefault(event.commandType(), new AtomicInteger()).decrementAndGet();

        Timer.builder(COMMANDS_DURATION)
            .description("Command execution time")
            .tag("command_type", event.commandType())
            .tag("operation_type", event.operationType())
            .register(registry)
            .record(event.duration());

        Counter.builder(COMMANDS_TOTAL)
            .description("Total commands processed")
            .tag("command_type", event.commandType())
            .tag("operation_type", event.operationType())
            .register(registry)
            .increment();
    }

    @EventListener
    public void handleCommandFailure(CommandFailureMetric event) {
        inFlightCommands.getOrDefault(event.commandType(), new AtomicInteger()).decrementAndGet();

        Counter.builder(COMMANDS_FAILED)
            .description("Failed commands")
            .tag("command_type", event.commandType())
            .tag("error_type", event.errorType())
            .register(registry)
            .increment();
    }

    @EventListener
    public void handleIdempotentOperation(IdempotentOperationMetric event) {
        Counter.builder(COMMANDS_IDEMPOTENT)
            .description("Idempotent operations (duplicate requests)")
            .tag("command_type", event.commandType())
            .register(registry)
            .increment();
    }

    // --- Outbox ---

    @EventListener
    public void handleEventsPublished(EventsPublishedMetric event) {
        Counter.builder(OUTBOX_EVENTS_PUBLISHED)
            .description("Total number of events published")
            .tag("publisher", event.publisherName())
            .register(registry)
            .increment(event.count());
    }

    @EventListener
    public void handlePublishingDuration(PublishingDurationMetric event) {
        Timer.builder(OUTBOX_PUBLISHING_DURATION)
            .description("Outbox publishing duration per publisher")
            .tag("publisher", event.publisherName())
            .register(registry)
            .record(event.duration());
    }

    @EventListener
    public void handleOutboxProcessingCycle(com.crablet.outbox.metrics.ProcessingCycleMetric event) {
        Counter.builder(OUTBOX_PROCESSING_CYCLES)
            .description("Total number of outbox processing cycles")
            .register(registry)
            .increment();
    }

    @EventListener
    public void handleOutboxError(OutboxErrorMetric event) {
        Counter.builder(OUTBOX_ERRORS)
            .description("Total number of publishing errors")
            .tag("publisher", event.publisherName())
            .register(registry)
            .increment();
    }

    // --- Poller / processor ---

    @EventListener
    public void handleLeadership(LeadershipMetric event) {
        String key = event.processorId() + "@" + event.instanceId();
        AtomicInteger leaderValue = leadershipState.computeIfAbsent(
            key,
            k -> {
                AtomicInteger value = new AtomicInteger(0);
                Gauge.builder(PROCESSOR_IS_LEADER, value, AtomicInteger::get)
                    .description("Whether this instance is the leader for the given processor (1=leader, 0=follower)")
                    .tag("processor", event.processorId())
                    .tag("instance_id", event.instanceId())
                    .register(registry);
                return value;
            }
        );
        leaderValue.set(event.isLeader() ? 1 : 0);
    }

    @EventListener
    public void handlePollerProcessingCycle(ProcessingCycleMetric event) {
        Counter.builder(POLLER_PROCESSING_CYCLES)
            .description("Total number of poller processing cycles")
            .tag("processor", event.processorId())
            .tag("instance_id", event.instanceId())
            .register(registry)
            .increment();

        Counter.builder(POLLER_EVENTS_FETCHED)
            .description("Total number of events fetched by poller")
            .tag("processor", event.processorId())
            .tag("instance_id", event.instanceId())
            .register(registry)
            .increment(event.eventsProcessed());

        if (event.empty()) {
            Counter.builder(POLLER_EMPTY_POLLS)
                .description("Total number of empty poll cycles")
                .tag("processor", event.processorId())
                .tag("instance_id", event.instanceId())
                .register(registry)
                .increment();
        }
    }

    @EventListener
    public void handleBackoffState(BackoffStateMetric event) {
        String key = event.processorId() + "@" + event.instanceId();

        backoffActiveState.computeIfAbsent(key, k -> {
            AtomicInteger gauge = new AtomicInteger(0);
            Gauge.builder(POLLER_BACKOFF_ACTIVE, gauge, AtomicInteger::get)
                .description("Whether the poller is in backoff mode (1=active, 0=normal)")
                .tag("processor", event.processorId())
                .tag("instance_id", event.instanceId())
                .register(registry);
            return gauge;
        }).set(event.active() ? 1 : 0);

        backoffEmptyPollState.computeIfAbsent(key, k -> {
            AtomicInteger gauge = new AtomicInteger(0);
            Gauge.builder(POLLER_BACKOFF_EMPTY_POLL_COUNT, gauge, AtomicInteger::get)
                .description("Consecutive empty poll count for the poller")
                .tag("processor", event.processorId())
                .tag("instance_id", event.instanceId())
                .register(registry);
            return gauge;
        }).set(event.emptyPollCount());
    }

    // --- Views ---

    @EventListener
    public void handleViewProjection(ViewProjectionMetric event) {
        Timer.builder(VIEWS_PROJECTION_DURATION)
            .description("View projection duration per batch")
            .tag("view", event.viewName())
            .register(registry)
            .record(event.duration());

        Counter.builder(VIEWS_EVENTS_PROJECTED)
            .description("Total number of events projected per view")
            .tag("view", event.viewName())
            .register(registry)
            .increment(event.eventsProjected());
    }

    @EventListener
    public void handleViewProjectionError(ViewProjectionErrorMetric event) {
        Counter.builder(VIEWS_PROJECTION_ERRORS)
            .description("Total number of view projection errors")
            .tag("view", event.viewName())
            .register(registry)
            .increment();
    }

    // --- Automations ---

    @EventListener
    public void handleAutomationExecution(AutomationExecutionMetric event) {
        Timer.builder(AUTOMATIONS_EXECUTION_DURATION)
            .description("Automation execution duration per batch")
            .tag("automation", event.automationName())
            .register(registry)
            .record(event.duration());

        Counter.builder(AUTOMATIONS_EVENTS_PROCESSED)
            .description("Total number of events processed per automation")
            .tag("automation", event.automationName())
            .register(registry)
            .increment(event.eventsProcessed());
    }

    @EventListener
    public void handleAutomationExecutionError(AutomationExecutionErrorMetric event) {
        Counter.builder(AUTOMATIONS_EXECUTION_ERRORS)
            .description("Total number of automation execution errors")
            .tag("automation", event.automationName())
            .register(registry)
            .increment();
    }
}
