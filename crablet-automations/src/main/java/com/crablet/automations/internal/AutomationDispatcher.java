package com.crablet.automations.internal;

import com.crablet.automations.AutomationHandler;
import com.crablet.automations.metrics.AutomationExecutionErrorMetric;
import com.crablet.automations.metrics.AutomationExecutionMetric;
import com.crablet.command.CommandExecutor;
import com.crablet.eventpoller.EventHandler;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.CorrelationContext;
import com.crablet.eventstore.StoredEvent;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Dispatches events to in-process automations.
 */
public class AutomationDispatcher implements EventHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(AutomationDispatcher.class);

    private final Map<String, AutomationHandler> handlers;
    private final CommandExecutor commandExecutor;
    private final ApplicationEventPublisher eventPublisher;
    private final ClockProvider clockProvider;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public AutomationDispatcher(
            Map<String, AutomationHandler> handlers,
            @Nullable CommandExecutor commandExecutor,
            ApplicationEventPublisher eventPublisher) {
        this(handlers, commandExecutor, eventPublisher, ClockProvider.systemDefault(), CircuitBreakerRegistry.ofDefaults());
    }

    public AutomationDispatcher(
            Map<String, AutomationHandler> handlers,
            @Nullable CommandExecutor commandExecutor,
            ApplicationEventPublisher eventPublisher,
            ClockProvider clockProvider) {
        this(handlers, commandExecutor, eventPublisher, clockProvider, CircuitBreakerRegistry.ofDefaults());
    }

    public AutomationDispatcher(
            Map<String, AutomationHandler> handlers,
            @Nullable CommandExecutor commandExecutor,
            ApplicationEventPublisher eventPublisher,
            ClockProvider clockProvider,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.handlers = handlers;
        this.commandExecutor = commandExecutor;
        this.eventPublisher = eventPublisher;
        this.clockProvider = clockProvider;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @Override
    public int handle(String automationName, List<StoredEvent> events) throws Exception {
        AutomationHandler handler = handlers.get(automationName);
        if (handler == null) {
            log.warn("No handler registered for automation: {}", automationName);
            return 0;
        }

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("automation-" + automationName);
        try {
            Callable<Integer> call = CircuitBreaker.decorateCallable(cb, () -> handleInProcess(handler, events));
            return call.call();
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker OPEN for automation {}", automationName);
            throw e;
        }
    }

    private int handleInProcess(AutomationHandler handler, List<StoredEvent> events) throws Exception {
        String automationName = handler.getAutomationName();
        Instant start = clockProvider.now();
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
                automationName, count, Duration.between(start, clockProvider.now())));
        log.debug("Automation {} (in-process) processed {} events", automationName, count);
        return count;
    }
}
