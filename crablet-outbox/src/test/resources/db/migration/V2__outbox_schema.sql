-- Outbox pattern: Track event publishing progress per topic and publisher
-- Each (topic, publisher) pair advances independently through the event stream
-- Leader election prevents concurrent processing by multiple instances

CREATE TABLE outbox_topic_progress (
    topic              VARCHAR(100)                NOT NULL,
    publisher           VARCHAR(100)                NOT NULL,
    last_position      BIGINT                      NOT NULL DEFAULT 0,
    last_published_at  TIMESTAMP WITH TIME ZONE,
    status             VARCHAR(20)                 NOT NULL DEFAULT 'ACTIVE',
    error_count        INT                         NOT NULL DEFAULT 0,
    last_error         TEXT,
    updated_at         TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    leader_instance    VARCHAR(255),
    leader_since       TIMESTAMP WITH TIME ZONE,
    leader_heartbeat   TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT pk_outbox_topic_progress PRIMARY KEY (topic, publisher),
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'PAUSED', 'FAILED'))
);

-- Indexes for querying and monitoring
CREATE INDEX idx_topic_status ON outbox_topic_progress(topic, status);
CREATE INDEX idx_topic_leader ON outbox_topic_progress(topic, leader_instance);
CREATE INDEX idx_topic_publisher_heartbeat ON outbox_topic_progress(topic, publisher, leader_heartbeat);

-- Comments explaining the design
COMMENT ON TABLE outbox_topic_progress IS 
'Tracks last published event position per publisher per topic. Each publisher advances independently through events matching its topic criteria.';

COMMENT ON COLUMN outbox_topic_progress.leader_instance IS 'Hostname/pod name of the instance currently holding the lock for this topic-publisher pair';

COMMENT ON COLUMN outbox_topic_progress.leader_since IS 'When the current instance became the leader';

COMMENT ON COLUMN outbox_topic_progress.leader_heartbeat IS 'Last heartbeat timestamp from the leader instance. Used to detect abandoned pairs when leader crashes.';

