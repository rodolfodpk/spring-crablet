-- View progress tracking table
-- Tracks processing progress for each view projection independently

CREATE TABLE IF NOT EXISTS view_progress (
    view_name VARCHAR(255) PRIMARY KEY,
    instance_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    last_position BIGINT NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    last_error_at TIMESTAMP WITH TIME ZONE,
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_view_progress_status ON view_progress(status);
CREATE INDEX idx_view_progress_instance ON view_progress(instance_id);
CREATE INDEX idx_view_progress_last_updated ON view_progress(last_updated_at);

COMMENT ON TABLE view_progress IS 
'Progress tracking for view projections. Each view tracks its own position independently.';

COMMENT ON COLUMN view_progress.view_name IS 
'Unique name of the view projection (e.g., "wallet-view")';

COMMENT ON COLUMN view_progress.instance_id IS 
'Instance ID of the leader processing this view (for leader election)';

COMMENT ON COLUMN view_progress.status IS 
'Status: ACTIVE, PAUSED, or FAILED';

COMMENT ON COLUMN view_progress.last_position IS 
'Last processed event position. Events with position > last_position will be processed.';

COMMENT ON COLUMN view_progress.error_count IS 
'Number of consecutive errors. View is marked FAILED if exceeds threshold.';

