DROP TABLE IF EXISTS test_single_key_progress;
CREATE TABLE test_single_key_progress (
    processor_id    VARCHAR(255)             PRIMARY KEY,
    instance_id     VARCHAR(255),
    status          VARCHAR(32)              NOT NULL DEFAULT 'ACTIVE',
    last_position   BIGINT                   NOT NULL DEFAULT 0,
    error_count     INT                      NOT NULL DEFAULT 0,
    last_error      TEXT,
    last_error_at   TIMESTAMP WITH TIME ZONE,
    last_updated_at TIMESTAMP WITH TIME ZONE
);
