-- Automation progress tracking table
-- Tracks processing progress for each automation independently

CREATE TABLE IF NOT EXISTS automation_progress (
    automation_name VARCHAR(255) PRIMARY KEY,
    instance_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    last_position BIGINT NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    last_error_at TIMESTAMP WITH TIME ZONE,
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_automation_progress_status ON automation_progress(status);
CREATE INDEX idx_automation_progress_instance ON automation_progress(instance_id);
CREATE INDEX idx_automation_progress_last_updated ON automation_progress(last_updated_at);

COMMENT ON TABLE automation_progress IS
'Progress tracking for event-driven automations. Each automation tracks its own position independently.';

COMMENT ON COLUMN automation_progress.automation_name IS
'Unique name of the automation (matches AutomationHandler.getAutomationName())';

COMMENT ON COLUMN automation_progress.instance_id IS
'Instance ID of the leader processing this automation (for leader election)';

COMMENT ON COLUMN automation_progress.status IS
'Status: ACTIVE, PAUSED, or FAILED';

COMMENT ON COLUMN automation_progress.last_position IS
'Last processed event position. Events with position > last_position will be processed.';

COMMENT ON COLUMN automation_progress.error_count IS
'Number of consecutive errors. Automation is marked FAILED if exceeds threshold.';
