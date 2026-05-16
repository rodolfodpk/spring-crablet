package com.crablet.metrics.micrometer;

import com.crablet.eventstore.metrics.MetricEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
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
 * Compatibility Micrometer collector for Crablet metric events.
 *
 * <p>This collector intentionally depends only on the shared {@link MetricEvent} marker and uses
 * event names plus record accessors reflectively. That keeps optional Crablet modules independent:
 * adding this compatibility collector no longer pulls commands, views, automations, outbox, and
 * event-poller onto the classpath.
 *
 * @deprecated Prefer module-owned Micrometer Observation instrumentation and Spring Boot
 * OTLP/OpenTelemetry export.
 */
@Deprecated(since = "1.0", forRemoval = false)
public class MicrometerMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(MicrometerMetricsCollector.class);

    private final MeterRegistry registry;

    private final Map<String, AtomicInteger> leadershipState = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> backoffActiveState = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> backoffEmptyPollState = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> inFlightCommands = new ConcurrentHashMap<>();

    public MicrometerMetricsCollector(MeterRegistry registry) {
        this.registry = registry;
        log.info("MicrometerMetricsCollector initialized in compatibility mode");
    }

    @EventListener
    public void handleMetricEvent(MetricEvent event) {
        switch (event.getClass().getSimpleName()) {
            case "EventsAppendedMetric" -> handleEventsAppended(event);
            case "EventTypeMetric" -> handleEventType(event);
            case "ConcurrencyViolationMetric" -> handleConcurrencyViolation();
            case "CommandStartedMetric" -> handleCommandStarted(event);
            case "CommandSuccessMetric" -> handleCommandSuccess(event);
            case "CommandFailureMetric" -> handleCommandFailure(event);
            case "IdempotentOperationMetric" -> handleIdempotentOperation(event);
            case "EventsPublishedMetric" -> handleEventsPublished(event);
            case "PublishingDurationMetric" -> handlePublishingDuration(event);
            case "OutboxErrorMetric" -> handleOutboxError(event);
            case "LeadershipMetric" -> handleLeadership(event);
            case "ProcessingCycleMetric" -> handleProcessingCycle(event);
            case "BackoffStateMetric" -> handleBackoffState(event);
            case "ViewProjectionMetric" -> handleViewProjection(event);
            case "ViewProjectionErrorMetric" -> handleViewProjectionError(event);
            case "AutomationExecutionMetric" -> handleAutomationExecution(event);
            case "AutomationExecutionErrorMetric" -> handleAutomationExecutionError(event);
            default -> log.debug("Ignoring unknown Crablet metric event {}", event.getClass().getName());
        }
    }

    private void handleEventsAppended(MetricEvent event) {
        Counter.builder(EVENTSTORE_EVENTS_APPENDED)
            .description("Total number of events appended to store")
            .register(registry)
            .increment(intValue(event, "count"));
    }

    private void handleEventType(MetricEvent event) {
        Counter.builder(EVENTSTORE_EVENTS_BY_TYPE)
            .description("Events appended by type")
            .tag("event_type", stringValue(event, "eventType"))
            .register(registry)
            .increment();
    }

    private void handleConcurrencyViolation() {
        Counter.builder(EVENTSTORE_CONCURRENCY_VIOLATIONS)
            .description("Total number of DCB concurrency violations")
            .register(registry)
            .increment();
    }

    private void handleCommandStarted(MetricEvent event) {
        String commandType = stringValue(event, "commandType");
        inFlightCommands.computeIfAbsent(commandType, k -> {
            AtomicInteger gauge = new AtomicInteger(0);
            Gauge.builder(COMMANDS_INFLIGHT, gauge, AtomicInteger::get)
                .description("Commands currently executing")
                .tag("command_type", k)
                .register(registry);
            return gauge;
        }).incrementAndGet();
    }

    private void handleCommandSuccess(MetricEvent event) {
        String commandType = stringValue(event, "commandType");
        inFlightCommands.getOrDefault(commandType, new AtomicInteger()).decrementAndGet();

        Timer.builder(COMMANDS_DURATION)
            .description("Command execution time")
            .tag("command_type", commandType)
            .tag("operation_type", stringValue(event, "operationType"))
            .register(registry)
            .record(durationValue(event, "duration"));

        Counter.builder(COMMANDS_TOTAL)
            .description("Total commands processed")
            .tag("command_type", commandType)
            .tag("operation_type", stringValue(event, "operationType"))
            .register(registry)
            .increment();
    }

    private void handleCommandFailure(MetricEvent event) {
        String commandType = stringValue(event, "commandType");
        inFlightCommands.getOrDefault(commandType, new AtomicInteger()).decrementAndGet();

        Counter.builder(COMMANDS_FAILED)
            .description("Failed commands")
            .tag("command_type", commandType)
            .tag("error_type", stringValue(event, "errorType"))
            .register(registry)
            .increment();
    }

    private void handleIdempotentOperation(MetricEvent event) {
        Counter.builder(COMMANDS_IDEMPOTENT)
            .description("Idempotent operations (duplicate requests)")
            .tag("command_type", stringValue(event, "commandType"))
            .register(registry)
            .increment();
    }

    private void handleEventsPublished(MetricEvent event) {
        Counter.builder(OUTBOX_EVENTS_PUBLISHED)
            .description("Total number of events published")
            .tag("publisher", stringValue(event, "publisherName"))
            .register(registry)
            .increment(intValue(event, "count"));
    }

    private void handlePublishingDuration(MetricEvent event) {
        Timer.builder(OUTBOX_PUBLISHING_DURATION)
            .description("Outbox publishing duration per publisher")
            .tag("publisher", stringValue(event, "publisherName"))
            .register(registry)
            .record(durationValue(event, "duration"));
    }

    private void handleOutboxError(MetricEvent event) {
        Counter.builder(OUTBOX_ERRORS)
            .description("Total number of publishing errors")
            .tag("publisher", stringValue(event, "publisherName"))
            .register(registry)
            .increment();
    }

    private void handleLeadership(MetricEvent event) {
        String processorId = stringValue(event, "processorId");
        String instanceId = stringValue(event, "instanceId");
        String key = processorId + "@" + instanceId;
        AtomicInteger leaderValue = leadershipState.computeIfAbsent(
            key,
            k -> {
                AtomicInteger value = new AtomicInteger(0);
                Gauge.builder(PROCESSOR_IS_LEADER, value, AtomicInteger::get)
                    .description("Whether this instance is the leader for the given processor (1=leader, 0=follower)")
                    .tag("processor", processorId)
                    .tag("instance_id", instanceId)
                    .register(registry);
                return value;
            }
        );
        leaderValue.set(booleanValue(event, "isLeader") ? 1 : 0);
    }

    private void handleProcessingCycle(MetricEvent event) {
        if (event.getClass().getPackageName().endsWith(".outbox.metrics")) {
            Counter.builder(OUTBOX_PROCESSING_CYCLES)
                .description("Total number of outbox processing cycles")
                .register(registry)
                .increment();
            return;
        }

        String processorId = stringValue(event, "processorId");
        String instanceId = stringValue(event, "instanceId");
        Counter.builder(POLLER_PROCESSING_CYCLES)
            .description("Total number of poller processing cycles")
            .tag("processor", processorId)
            .tag("instance_id", instanceId)
            .register(registry)
            .increment();

        Counter.builder(POLLER_EVENTS_FETCHED)
            .description("Total number of events fetched by poller")
            .tag("processor", processorId)
            .tag("instance_id", instanceId)
            .register(registry)
            .increment(intValue(event, "eventsProcessed"));

        if (booleanValue(event, "empty")) {
            Counter.builder(POLLER_EMPTY_POLLS)
                .description("Total number of empty poll cycles")
                .tag("processor", processorId)
                .tag("instance_id", instanceId)
                .register(registry)
                .increment();
        }
    }

    private void handleBackoffState(MetricEvent event) {
        String processorId = stringValue(event, "processorId");
        String instanceId = stringValue(event, "instanceId");
        String key = processorId + "@" + instanceId;

        backoffActiveState.computeIfAbsent(key, k -> {
            AtomicInteger gauge = new AtomicInteger(0);
            Gauge.builder(POLLER_BACKOFF_ACTIVE, gauge, AtomicInteger::get)
                .description("Whether the poller is in backoff mode (1=active, 0=normal)")
                .tag("processor", processorId)
                .tag("instance_id", instanceId)
                .register(registry);
            return gauge;
        }).set(booleanValue(event, "active") ? 1 : 0);

        backoffEmptyPollState.computeIfAbsent(key, k -> {
            AtomicInteger gauge = new AtomicInteger(0);
            Gauge.builder(POLLER_BACKOFF_EMPTY_POLL_COUNT, gauge, AtomicInteger::get)
                .description("Consecutive empty poll count for the poller")
                .tag("processor", processorId)
                .tag("instance_id", instanceId)
                .register(registry);
            return gauge;
        }).set(intValue(event, "emptyPollCount"));
    }

    private void handleViewProjection(MetricEvent event) {
        Timer.builder(VIEWS_PROJECTION_DURATION)
            .description("View projection duration per batch")
            .tag("view", stringValue(event, "viewName"))
            .register(registry)
            .record(durationValue(event, "duration"));

        Counter.builder(VIEWS_EVENTS_PROJECTED)
            .description("Total number of events projected per view")
            .tag("view", stringValue(event, "viewName"))
            .register(registry)
            .increment(intValue(event, "eventsProjected"));
    }

    private void handleViewProjectionError(MetricEvent event) {
        Counter.builder(VIEWS_PROJECTION_ERRORS)
            .description("Total number of view projection errors")
            .tag("view", stringValue(event, "viewName"))
            .register(registry)
            .increment();
    }

    private void handleAutomationExecution(MetricEvent event) {
        Timer.builder(AUTOMATIONS_EXECUTION_DURATION)
            .description("Automation execution duration per batch")
            .tag("automation", stringValue(event, "automationName"))
            .register(registry)
            .record(durationValue(event, "duration"));

        Counter.builder(AUTOMATIONS_EVENTS_PROCESSED)
            .description("Total number of events processed per automation")
            .tag("automation", stringValue(event, "automationName"))
            .register(registry)
            .increment(intValue(event, "eventsProcessed"));
    }

    private void handleAutomationExecutionError(MetricEvent event) {
        Counter.builder(AUTOMATIONS_EXECUTION_ERRORS)
            .description("Total number of automation execution errors")
            .tag("automation", stringValue(event, "automationName"))
            .register(registry)
            .increment();
    }

    private static String stringValue(MetricEvent event, String accessor) {
        return (String) value(event, accessor);
    }

    private static int intValue(MetricEvent event, String accessor) {
        return ((Number) value(event, accessor)).intValue();
    }

    private static boolean booleanValue(MetricEvent event, String accessor) {
        return (Boolean) value(event, accessor);
    }

    private static Duration durationValue(MetricEvent event, String accessor) {
        return (Duration) value(event, accessor);
    }

    private static Object value(MetricEvent event, String accessor) {
        try {
            return event.getClass().getMethod(accessor).invoke(event);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(
                    "Crablet metric event " + event.getClass().getName() +
                            " does not expose expected accessor " + accessor + "()", e);
        }
    }
}
