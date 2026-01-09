package com.crablet.views;

import com.crablet.eventprocessor.EventHandler;

/**
 * Marker interface for view projectors.
 * Projectors implementing this interface declare which view they handle.
 */
public interface ViewProjector extends EventHandler<String> {
    
    /**
     * Get the view name this projector handles.
     */
    String getViewName();
}

