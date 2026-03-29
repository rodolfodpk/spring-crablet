-- Reaction progress tracking table
-- Tracks processing progress for each reaction independently

CREATE TABLE IF NOT EXISTS reaction_progress (
    reaction_name VARCHAR(255) PRIMARY KEY,
    instance_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    last_position BIGINT NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    last_error_at TIMESTAMP WITH TIME ZONE,
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reaction_progress_status ON reaction_progress(status);
CREATE INDEX idx_reaction_progress_instance ON reaction_progress(instance_id);
CREATE INDEX idx_reaction_progress_last_updated ON reaction_progress(last_updated_at);

COMMENT ON TABLE reaction_progress IS
'Progress tracking for event-driven reactions. Each reaction tracks its own position independently.';

COMMENT ON COLUMN reaction_progress.reaction_name IS
'Unique name of the reaction (matches Reaction.getReactionName())';

COMMENT ON COLUMN reaction_progress.instance_id IS
'Instance ID of the leader processing this reaction (for leader election)';

COMMENT ON COLUMN reaction_progress.status IS
'Status: ACTIVE, PAUSED, or FAILED';

COMMENT ON COLUMN reaction_progress.last_position IS
'Last processed event position. Events with position > last_position will be processed.';

COMMENT ON COLUMN reaction_progress.error_count IS
'Number of consecutive errors. Reaction is marked FAILED if exceeds threshold.';
