package com.crablet.views.adapter;

import com.crablet.eventpoller.EventHandler;
import com.crablet.eventstore.StoredEvent;
import com.crablet.views.ViewProjector;
import com.crablet.views.metrics.ViewProjectionErrorMetric;
import com.crablet.views.metrics.ViewProjectionMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Event handler for view projections.
 * Delegates to user-provided ViewProjector implementations registered per view.
 */
public class ViewEventHandler implements EventHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(ViewEventHandler.class);

    private final Map<String, ViewProjector> projectors;
    private final ApplicationEventPublisher eventPublisher;

    public ViewEventHandler(List<ViewProjector> projectors, ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.projectors = new HashMap<>();
        for (ViewProjector projector : projectors) {
            String viewName = projector.getViewName();
            this.projectors.put(viewName, projector);
            log.debug("Registered projector {} for view {}", projector.getClass().getSimpleName(), viewName);
        }
    }

    @Override
    public int handle(String viewName, List<StoredEvent> events, DataSource writeDataSource) throws Exception {
        ViewProjector projector = projectors.get(viewName);

        if (projector == null) {
            log.warn("No projector registered for view: {}", viewName);
            return 0;
        }

        Instant start = Instant.now();
        try {
            int handled = projector.handle(viewName, events, writeDataSource);
            eventPublisher.publishEvent(new ViewProjectionMetric(viewName, handled, Duration.between(start, Instant.now())));
            log.debug("Projector {} handled {} events for view {}",
                projector.getClass().getSimpleName(), handled, viewName);
            return handled;
        } catch (Exception e) {
            eventPublisher.publishEvent(new ViewProjectionErrorMetric(viewName));
            log.error("Projector {} failed for view {}: {}",
                projector.getClass().getSimpleName(), viewName, e.getMessage(), e);
            throw e;
        }
    }
}

