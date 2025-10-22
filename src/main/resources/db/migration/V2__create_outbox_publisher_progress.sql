-- Publisher progress tracking (one row per publisher)
-- Each publisher independently tracks its last published position
CREATE TABLE outbox_publisher_progress (
    publisher_name    VARCHAR(100) PRIMARY KEY,
    last_position     BIGINT NOT NULL DEFAULT 0,
    last_published_at TIMESTAMP WITH TIME ZONE,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    error_count       INT NOT NULL DEFAULT 0,
    last_error        TEXT,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'PAUSED', 'FAILED'))
);

-- Index for monitoring and querying publisher status
CREATE INDEX idx_publisher_status_position ON outbox_publisher_progress (status, last_position);

-- Comment explaining the design
COMMENT ON TABLE outbox_publisher_progress IS 
'Tracks last published event position per publisher. Each publisher advances independently through the event stream.';

-- No trigger needed! Publishers query: SELECT * FROM events WHERE position > last_position
