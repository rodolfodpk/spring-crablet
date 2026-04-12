ALTER TABLE IF EXISTS reaction_progress RENAME TO automation_progress;

ALTER TABLE IF EXISTS automation_progress
    RENAME COLUMN reaction_name TO automation_name;

ALTER INDEX IF EXISTS idx_reaction_progress_status
    RENAME TO idx_automation_progress_status;

ALTER INDEX IF EXISTS idx_reaction_progress_instance
    RENAME TO idx_automation_progress_instance;

ALTER INDEX IF EXISTS idx_reaction_progress_last_updated
    RENAME TO idx_automation_progress_last_updated;

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
