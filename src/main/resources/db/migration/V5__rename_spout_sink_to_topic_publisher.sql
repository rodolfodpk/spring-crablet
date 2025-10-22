-- Rename table
ALTER TABLE outbox_spout_progress RENAME TO outbox_topic_progress;

-- Rename columns
ALTER TABLE outbox_topic_progress RENAME COLUMN spout TO topic;
ALTER TABLE outbox_topic_progress RENAME COLUMN sink TO publisher;

-- Update indexes (drop old, create new)
DROP INDEX IF EXISTS idx_spout_status;
DROP INDEX IF EXISTS idx_spout_leader;
CREATE INDEX idx_topic_status ON outbox_topic_progress(topic, status);
CREATE INDEX idx_topic_leader ON outbox_topic_progress(topic, leader_instance);

-- Update table comment
COMMENT ON TABLE outbox_topic_progress IS 
'Tracks last published event position per publisher per topic. Each publisher advances independently through events matching its topic criteria.';
