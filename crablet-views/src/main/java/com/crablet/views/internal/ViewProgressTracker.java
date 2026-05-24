package com.crablet.views.internal;

import com.crablet.eventpoller.progress.AbstractSingleKeyProgressTracker;

import javax.sql.DataSource;

/**
 * Progress tracker for view projections. Uses the {@code crablet_view_progress} table.
 */
public class ViewProgressTracker extends AbstractSingleKeyProgressTracker {

    public ViewProgressTracker(DataSource dataSource) {
        super(dataSource, "crablet_view_progress", "view_name");
    }
}
