package com.crablet.views.adapter;

import com.crablet.eventprocessor.EventHandler;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.views.ViewProjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
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
    
    public ViewEventHandler(List<ViewProjector> projectors) {
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
        
        try {
            int handled = projector.handle(viewName, events, writeDataSource);
            log.debug("Projector {} handled {} events for view {}", 
                projector.getClass().getSimpleName(), handled, viewName);
            return handled;
        } catch (Exception e) {
            log.error("Projector {} failed for view {}: {}", 
                projector.getClass().getSimpleName(), viewName, e.getMessage(), e);
            throw e;
        }
    }
}

