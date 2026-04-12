package com.crablet.automations.internal;

import com.crablet.eventpoller.progress.AbstractSingleKeyProgressTracker;

import javax.sql.DataSource;

/**
 * Progress tracker for automations. Uses the {@code automation_progress} table.
 */
public class AutomationProgressTracker extends AbstractSingleKeyProgressTracker {

    public AutomationProgressTracker(DataSource dataSource) {
        super(dataSource, "automation_progress", "automation_name");
    }
}
