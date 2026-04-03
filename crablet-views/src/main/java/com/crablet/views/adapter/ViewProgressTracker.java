package com.crablet.views.adapter;

import com.crablet.eventpoller.progress.AbstractSingleKeyProgressTracker;

import javax.sql.DataSource;

/**
 * Progress tracker for view projections. Uses the {@code view_progress} table.
 */
public class ViewProgressTracker extends AbstractSingleKeyProgressTracker {

    public ViewProgressTracker(DataSource dataSource) {
        super(dataSource, "view_progress", "view_name");
    }
}
