-- Add leader tracking to outbox_publisher_progress
ALTER TABLE outbox_publisher_progress
ADD COLUMN leader_instance VARCHAR(255),
ADD COLUMN leader_since TIMESTAMP WITH TIME ZONE;

-- Index for querying current leaders
CREATE INDEX idx_publisher_leader ON outbox_publisher_progress (leader_instance, status);

-- Add comment for documentation
COMMENT ON COLUMN outbox_publisher_progress.leader_instance IS 'Hostname/pod name of the instance currently holding the lock for this publisher';
COMMENT ON COLUMN outbox_publisher_progress.leader_since IS 'When the current instance became the leader';

