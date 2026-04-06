package com.crablet.automations.internal;

import com.crablet.eventpoller.progress.AbstractSingleKeyProgressTracker;

import javax.sql.DataSource;

/**
 * Progress tracker for automations. Uses the {@code reaction_progress} table.
 */
public class AutomationProgressTracker extends AbstractSingleKeyProgressTracker {

    public AutomationProgressTracker(DataSource dataSource) {
        super(dataSource, "reaction_progress", "reaction_name");
    }
}
