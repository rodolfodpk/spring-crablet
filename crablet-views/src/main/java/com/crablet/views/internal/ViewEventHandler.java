package com.crablet.views.internal;

import com.crablet.eventpoller.EventHandler;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.StoredEvent;
import com.crablet.views.ViewProjector;
import com.crablet.views.metrics.ViewProjectionErrorMetric;
import com.crablet.views.metrics.ViewProjectionMetric;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Event handler for view projections.
 * Delegates to user-provided ViewProjector implementations registered per view.
 * <p>
 * The write datasource is owned by each ViewProjector (injected at construction time),
 * not passed here. This handler is responsible only for routing and metrics.
 */
public class ViewEventHandler implements EventHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(ViewEventHandler.class);

    private final Map<String, ViewProjector> projectors;
    private final ApplicationEventPublisher eventPublisher;
    private final ClockProvider clockProvider;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public ViewEventHandler(
            List<ViewProjector> projectors,
            ApplicationEventPublisher eventPublisher) {
        this(projectors, eventPublisher, ClockProvider.systemDefault(), CircuitBreakerRegistry.ofDefaults());
    }

    public ViewEventHandler(
            List<ViewProjector> projectors,
            ApplicationEventPublisher eventPublisher,
            ClockProvider clockProvider) {
        this(projectors, eventPublisher, clockProvider, CircuitBreakerRegistry.ofDefaults());
    }

    public ViewEventHandler(
            List<ViewProjector> projectors,
            ApplicationEventPublisher eventPublisher,
            ClockProvider clockProvider,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.eventPublisher = eventPublisher;
        this.clockProvider = clockProvider;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.projectors = new HashMap<>();
        for (ViewProjector projector : projectors) {
            String viewName = projector.getViewName();
            this.projectors.put(viewName, projector);
            log.debug("Registered projector {} for view {}", projector.getClass().getSimpleName(), viewName);
        }
    }

    @Override
    public int handle(String viewName, List<StoredEvent> events) throws Exception {
        ViewProjector projector = projectors.get(viewName);

        if (projector == null) {
            log.warn("No projector registered for view: {}", viewName);
            return 0;
        }

        Instant start = clockProvider.now();
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("view-" + viewName);
        try {
            Callable<Integer> call = CircuitBreaker.decorateCallable(cb, () -> projector.handle(viewName, events));
            int handled = call.call();
            eventPublisher.publishEvent(new ViewProjectionMetric(viewName, handled, Duration.between(start, clockProvider.now())));
            log.debug("Projector {} handled {} events for view {}",
                projector.getClass().getSimpleName(), handled, viewName);
            return handled;
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker OPEN for view {}", viewName);
            throw e;
        } catch (Exception e) {
            eventPublisher.publishEvent(new ViewProjectionErrorMetric(viewName));
            log.error("Projector {} failed for view {}: {}",
                projector.getClass().getSimpleName(), viewName, e.getMessage(), e);
            throw e;
        }
    }
}
