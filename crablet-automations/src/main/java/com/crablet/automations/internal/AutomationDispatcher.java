package com.crablet.automations.internal;

import com.crablet.automations.AutomationHandler;
import com.crablet.automations.metrics.AutomationExecutionErrorMetric;
import com.crablet.automations.metrics.AutomationExecutionMetric;
import com.crablet.command.CommandExecutor;
import com.crablet.eventpoller.EventHandler;
import com.crablet.eventstore.CorrelationContext;
import com.crablet.eventstore.StoredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Dispatches events to automations — either via HTTP POST or directly in-process.
 *
 * <p>Routing is determined by the handler itself: if
 * {@link AutomationHandler#getWebhookUrl()} is non-blank, the matching event is POSTed
 * to that URL; otherwise {@link AutomationHandler#react(StoredEvent, CommandExecutor)}
 * is invoked directly.
 */
public class AutomationDispatcher implements EventHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(AutomationDispatcher.class);

    private final Map<String, AutomationHandler> handlers;
    private final AutomationWebhookClient webhookClient;
    private final CommandExecutor commandExecutor;
    private final ApplicationEventPublisher eventPublisher;
    private final Environment environment;

    public AutomationDispatcher(
            Map<String, AutomationHandler> handlers,
            AutomationWebhookClient webhookClient,
            @Nullable CommandExecutor commandExecutor,
            ApplicationEventPublisher eventPublisher,
            Environment environment) {
        this.handlers = handlers;
        this.webhookClient = webhookClient;
        this.commandExecutor = commandExecutor;
        this.eventPublisher = eventPublisher;
        this.environment = environment;
    }

    @Override
    public int handle(String automationName, List<StoredEvent> events) throws Exception {
        AutomationHandler handler = handlers.get(automationName);
        if (handler == null) {
            log.warn("No handler registered for automation: {}", automationName);
            return 0;
        }

        if (StringUtils.hasText(handler.getWebhookUrl())) {
            return handleWebhook(handler, events);
        }
        return handleInProcess(handler, events);
    }

    private int handleInProcess(AutomationHandler handler, List<StoredEvent> events) throws Exception {
        String automationName = handler.getAutomationName();
        Instant start = Instant.now();
        int count = 0;

        for (StoredEvent event : events) {
            try {
                // Propagate correlation and set causation for the downstream command execution.
                // ScopedValue scope exits automatically — no finally/clear needed.
                var scope = ScopedValue.where(CorrelationContext.CAUSATION_ID, event.position());
                if (event.correlationId() != null) {
                    scope = scope.where(CorrelationContext.CORRELATION_ID, event.correlationId());
                }
                scope.call(() -> {
                    handler.react(event, commandExecutor);
                    return null;
                });
                count++;
            } catch (Exception e) {
                eventPublisher.publishEvent(new AutomationExecutionErrorMetric(automationName));
                log.error("Automation {} (in-process) failed on event type={} position={}: {}",
                        automationName, event.type(), event.position(), e.getMessage(), e);
                throw e;
            }
        }

        eventPublisher.publishEvent(new AutomationExecutionMetric(
                automationName, count, Duration.between(start, Instant.now())));
        log.debug("Automation {} (in-process) processed {} events", automationName, count);
        return count;
    }

    private int handleWebhook(AutomationHandler handler, List<StoredEvent> events) throws Exception {
        String automationName = handler.getAutomationName();
        String webhookUrl = resolveWebhookUrl(handler.getWebhookUrl());
        Instant start = Instant.now();
        int count = 0;

        for (StoredEvent event : events) {
            try {
                webhookClient.postEvent(webhookUrl, handler, event);
                count++;
            } catch (Exception e) {
                eventPublisher.publishEvent(new AutomationExecutionErrorMetric(automationName));
                log.error("Automation {} failed to POST event type={} position={} to {}: {}",
                        automationName, event.type(), event.position(), webhookUrl, e.getMessage(), e);
                throw e;
            }
        }

        eventPublisher.publishEvent(new AutomationExecutionMetric(
                automationName, count, Duration.between(start, Instant.now())));
        log.debug("Automation {} dispatched {} events to {}", automationName, count, webhookUrl);
        return count;
    }
    private String resolveWebhookUrl(String webhookUrl) {
        if (webhookUrl.startsWith("http://") || webhookUrl.startsWith("https://")) {
            return webhookUrl;
        }
        if (!webhookUrl.startsWith("/")) {
            throw new IllegalArgumentException("webhookUrl must be absolute or start with '/': " + webhookUrl);
        }

        Integer port = environment.getProperty("local.server.port", Integer.class);
        if (port == null || port <= 0) {
            port = environment.getProperty("server.port", Integer.class);
        }
        if (port == null || port <= 0) {
            throw new IllegalStateException(
                    "Relative automation webhookUrl requires local.server.port or server.port: " + webhookUrl
            );
        }

        return "http://localhost:" + port + webhookUrl;
    }
}
