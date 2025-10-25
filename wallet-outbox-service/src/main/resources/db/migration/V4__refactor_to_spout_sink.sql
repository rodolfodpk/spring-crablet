-- Refactor outbox_publisher_progress to outbox_spout_progress
-- Support spout-sink architecture with composite primary key

-- Rename table
ALTER TABLE outbox_publisher_progress RENAME TO outbox_spout_progress;

-- Rename column
ALTER TABLE outbox_spout_progress RENAME COLUMN publisher_name TO sink;

-- Add spout column
ALTER TABLE outbox_spout_progress ADD COLUMN spout VARCHAR(100);

-- Migrate existing data to default spout
UPDATE outbox_spout_progress SET spout = 'default' WHERE spout IS NULL;

-- Update primary key
ALTER TABLE outbox_spout_progress DROP CONSTRAINT outbox_publisher_progress_pkey;
ALTER TABLE outbox_spout_progress ADD PRIMARY KEY (spout, sink);

-- Update indexes
DROP INDEX IF EXISTS idx_publisher_status_position;
DROP INDEX IF EXISTS idx_publisher_leader;
CREATE INDEX idx_spout_status ON outbox_spout_progress(spout, status);
CREATE INDEX idx_spout_leader ON outbox_spout_progress(spout, leader_instance);

-- Add GIN index to events for efficient tag queries
CREATE INDEX IF NOT EXISTS idx_events_tags_gin ON events USING GIN(tags);

-- Add comment explaining the new design
COMMENT ON TABLE outbox_spout_progress IS 
'Tracks last published event position per sink per spout. Each sink advances independently through events matching its spout criteria.';
