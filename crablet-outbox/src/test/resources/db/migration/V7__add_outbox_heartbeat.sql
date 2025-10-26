-- Add heartbeat column for leader liveness detection
ALTER TABLE outbox_topic_progress
ADD COLUMN leader_heartbeat TIMESTAMP WITH TIME ZONE;

-- Update existing rows to have current timestamp if they have a leader
UPDATE outbox_topic_progress
SET leader_heartbeat = CURRENT_TIMESTAMP
WHERE leader_instance IS NOT NULL;

-- Add index for efficient stale heartbeat queries
CREATE INDEX idx_topic_publisher_heartbeat 
ON outbox_topic_progress(topic, publisher, leader_heartbeat);

-- Add comment
COMMENT ON COLUMN outbox_topic_progress.leader_heartbeat IS 
'Last heartbeat timestamp from the leader instance. Used to detect abandoned pairs when leader crashes.';

