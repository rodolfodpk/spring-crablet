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
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that every metric event produces the expected Prometheus text format output.
 * Uses PrometheusMeterRegistry directly — no Spring context, no database.
 * <p>
 * This test guards against metric regressions (renamed metrics, missing labels) that would
 * silently break Grafana dashboards or alert rules.
 */
@SuppressWarnings("NullAway")
@DisplayName("Prometheus metric exposition format")
class PrometheusMetricsExpositionTest {

    private PrometheusMeterRegistry registry;
    private MicrometerMetricsCollector collector;

    @BeforeEach
    void setUp() {
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        collector = new MicrometerMetricsCollector(registry);
    }

    @Test
    @DisplayName("EventStore metrics produce correct Prometheus names")
    void eventStoreMetrics() {
        collector.handleEventsAppended(new EventsAppendedMetric(3));
        collector.handleEventType(new EventTypeMetric("WalletOpened"));
        collector.handleConcurrencyViolation(new ConcurrencyViolationMetric());

        String output = scrape();
        assertThat(output).contains("eventstore_events_appended_total 3.0");
        assertThat(output).contains("eventstore_events_by_type_total{event_type=\"WalletOpened\"}");
        assertThat(output).contains("eventstore_concurrency_violations_total 1.0");
    }

    @Test
    @DisplayName("Command metrics produce correct Prometheus names and labels")
    void commandMetrics() {
        collector.handleCommandStarted(new CommandStartedMetric("OpenWalletCommand", Instant.now()));
        collector.handleCommandSuccess(new CommandSuccessMetric("OpenWalletCommand", Duration.ofMillis(50), "idempotent"));
        collector.handleCommandFailure(new CommandFailureMetric("WithdrawCommand", "InsufficientFunds"));
        collector.handleIdempotentOperation(new IdempotentOperationMetric("OpenWalletCommand"));

        String output = scrape();
        assertThat(output).contains("commands_inflight{command_type=\"OpenWalletCommand\"}");
        assertThat(output).contains("eventstore_commands_duration_seconds_count{command_type=\"OpenWalletCommand\",operation_type=\"idempotent\"}");
        assertThat(output).contains("eventstore_commands_duration_seconds_sum{command_type=\"OpenWalletCommand\",operation_type=\"idempotent\"}");
        // The new Prometheus client does not double-append _total when the Micrometer name already ends in .total
        assertThat(output).contains("eventstore_commands_total{command_type=\"OpenWalletCommand\",operation_type=\"idempotent\"}");
        assertThat(output).contains("eventstore_commands_failed_total{command_type=\"WithdrawCommand\",error_type=\"InsufficientFunds\"}");
        assertThat(output).contains("eventstore_commands_idempotent_total{command_type=\"OpenWalletCommand\"}");
    }

    @Test
    @DisplayName("Outbox metrics produce correct Prometheus names and labels")
    void outboxMetrics() {
        collector.handleEventsPublished(new EventsPublishedMetric("LogPublisher", 5));
        collector.handlePublishingDuration(new PublishingDurationMetric("LogPublisher", Duration.ofMillis(10)));
        collector.handleOutboxProcessingCycle(new com.crablet.outbox.metrics.ProcessingCycleMetric());
        collector.handleOutboxError(new OutboxErrorMetric("LogPublisher"));

        String output = scrape();
        assertThat(output).contains("outbox_events_published_total{publisher=\"LogPublisher\"} 5.0");
        assertThat(output).contains("outbox_publishing_duration_seconds_count{publisher=\"LogPublisher\"}");
        assertThat(output).contains("outbox_processing_cycles_total 1.0");
        assertThat(output).contains("outbox_errors_total{publisher=\"LogPublisher\"} 1.0");
    }

    @Test
    @DisplayName("Leadership metric uses instance_id label (not instance)")
    void leadershipMetricLabel() {
        collector.handleLeadership(new LeadershipMetric("outbox", "node-1", true));

        String output = scrape();
        // Correct label key is instance_id
        assertThat(output).contains("processor_is_leader{instance_id=\"node-1\",processor=\"outbox\"} 1.0");
        // Old label key must not appear
        assertThat(output).doesNotContain("instance=\"node-1\"");
    }

    @Test
    @DisplayName("Poller metrics produce correct Prometheus names and labels")
    void pollerMetrics() {
        collector.handlePollerProcessingCycle(new ProcessingCycleMetric("views", "node-1", 10, false));
        collector.handleBackoffState(new BackoffStateMetric("views", "node-1", true, 3));

        String output = scrape();
        assertThat(output).contains("poller_processing_cycles_total{instance_id=\"node-1\",processor=\"views\"}");
        assertThat(output).contains("poller_events_fetched_total{instance_id=\"node-1\",processor=\"views\"} 10.0");
        assertThat(output).contains("poller_backoff_active{instance_id=\"node-1\",processor=\"views\"} 1.0");
        assertThat(output).contains("poller_backoff_empty_poll_count{instance_id=\"node-1\",processor=\"views\"} 3.0");
    }

    @Test
    @DisplayName("View metrics produce correct Prometheus names and labels")
    void viewMetrics() {
        collector.handleViewProjection(new ViewProjectionMetric("wallet_balance", 7, Duration.ofMillis(20)));
        collector.handleViewProjectionError(new ViewProjectionErrorMetric("wallet_balance"));

        String output = scrape();
        assertThat(output).contains("views_projection_duration_seconds_count{view=\"wallet_balance\"}");
        assertThat(output).contains("views_events_projected_total{view=\"wallet_balance\"} 7.0");
        assertThat(output).contains("views_projection_errors_total{view=\"wallet_balance\"} 1.0");
    }

    @Test
    @DisplayName("Automation metrics produce correct Prometheus names and labels")
    void automationMetrics() {
        collector.handleAutomationExecution(new AutomationExecutionMetric("refund-automation", 2, Duration.ofMillis(30)));
        collector.handleAutomationExecutionError(new AutomationExecutionErrorMetric("refund-automation"));

        String output = scrape();
        assertThat(output).contains("automations_execution_duration_seconds_count{automation=\"refund-automation\"}");
        assertThat(output).contains("automations_events_processed_total{automation=\"refund-automation\"} 2.0");
        assertThat(output).contains("automations_execution_errors_total{automation=\"refund-automation\"} 1.0");
    }

    private String scrape() {
        return registry.scrape();
    }
}
