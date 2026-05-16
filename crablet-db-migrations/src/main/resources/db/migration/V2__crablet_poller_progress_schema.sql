-- Crablet poller progress schema.
--
-- These tables are framework-owned cursors and status records for outbox,
-- views, automations, and shared-fetch module scans.

CREATE TABLE outbox_topic_progress
(
    topic             TEXT                     NOT NULL,
    publisher         TEXT                     NOT NULL,
    last_position     BIGINT                   NOT NULL DEFAULT 0,
    last_published_at TIMESTAMP WITH TIME ZONE,
    status            TEXT                     NOT NULL DEFAULT 'ACTIVE',
    error_count       INT                      NOT NULL DEFAULT 0,
    last_error        TEXT,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    leader_instance   TEXT,
    leader_since      TIMESTAMP WITH TIME ZONE,
    leader_heartbeat  TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_outbox_topic_progress PRIMARY KEY (topic, publisher),
    CONSTRAINT chk_outbox_status CHECK (status IN ('ACTIVE', 'PAUSED', 'FAILED')),
    CONSTRAINT chk_topic_len CHECK (length(topic) <= 100),
    CONSTRAINT chk_publisher_len CHECK (length(publisher) <= 100),
    CONSTRAINT chk_leader_instance_len CHECK (length(leader_instance) <= 255)
);

CREATE TABLE view_progress
(
    view_name       TEXT                     PRIMARY KEY,
    instance_id     TEXT,
    status          TEXT                     NOT NULL DEFAULT 'ACTIVE',
    last_position   BIGINT                   NOT NULL DEFAULT 0,
    error_count     INTEGER                  NOT NULL DEFAULT 0,
    last_error      TEXT,
    last_error_at   TIMESTAMP WITH TIME ZONE,
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_view_status CHECK (status IN ('ACTIVE', 'PAUSED', 'FAILED')),
    CONSTRAINT chk_view_name_len CHECK (length(view_name) <= 255),
    CONSTRAINT chk_view_instance_len CHECK (length(instance_id) <= 255)
);

CREATE TABLE automation_progress
(
    automation_name TEXT                     PRIMARY KEY,
    instance_id     TEXT,
    status          TEXT                     NOT NULL DEFAULT 'ACTIVE',
    last_position   BIGINT                   NOT NULL DEFAULT 0,
    error_count     INTEGER                  NOT NULL DEFAULT 0,
    last_error      TEXT,
    last_error_at   TIMESTAMP WITH TIME ZONE,
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_automation_status CHECK (status IN ('ACTIVE', 'PAUSED', 'FAILED')),
    CONSTRAINT chk_automation_name_len CHECK (length(automation_name) <= 255),
    CONSTRAINT chk_automation_instance_len CHECK (length(instance_id) <= 255)
);

CREATE TABLE crablet_module_scan_progress
(
    module_name   TEXT   PRIMARY KEY,
    scan_position BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE crablet_processor_scan_progress
(
    module_name      TEXT   NOT NULL,
    processor_id     TEXT   NOT NULL,
    scanned_position BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (module_name, processor_id)
);

CREATE INDEX idx_topic_status ON outbox_topic_progress (topic, status);
CREATE INDEX idx_topic_leader ON outbox_topic_progress (topic, leader_instance);
CREATE INDEX idx_topic_publisher_heartbeat ON outbox_topic_progress (topic, publisher, leader_heartbeat);

CREATE INDEX idx_view_progress_status ON view_progress (status);
CREATE INDEX idx_view_progress_instance ON view_progress (instance_id);
CREATE INDEX idx_view_progress_last_updated ON view_progress (last_updated_at);

CREATE INDEX idx_automation_progress_status ON automation_progress (status);
CREATE INDEX idx_automation_progress_instance ON automation_progress (instance_id);
CREATE INDEX idx_automation_progress_last_updated ON automation_progress (last_updated_at);

COMMENT ON TABLE outbox_topic_progress IS
    'Tracks last published event position per publisher per topic. Each publisher advances independently through events matching its topic criteria.';

COMMENT ON COLUMN outbox_topic_progress.leader_instance IS
    'Hostname/pod name of the instance currently holding the lock for this topic-publisher pair.';

COMMENT ON COLUMN outbox_topic_progress.leader_since IS
    'When the current instance became the leader.';

COMMENT ON COLUMN outbox_topic_progress.leader_heartbeat IS
    'Last heartbeat timestamp from the leader instance. Used to detect abandoned pairs when leader crashes.';

COMMENT ON TABLE view_progress IS
    'Progress tracking for view projections. Each view tracks its own position independently.';

COMMENT ON COLUMN view_progress.view_name IS
    'Unique name of the view projection.';

COMMENT ON COLUMN view_progress.instance_id IS
    'Instance ID of the leader processing this view.';

COMMENT ON COLUMN view_progress.status IS
    'Status: ACTIVE, PAUSED, or FAILED.';

COMMENT ON COLUMN view_progress.last_position IS
    'Last processed event position. Events with position > last_position will be processed.';

COMMENT ON COLUMN view_progress.error_count IS
    'Number of consecutive errors. View is marked FAILED if it exceeds the configured threshold.';

COMMENT ON TABLE automation_progress IS
    'Progress tracking for event-driven automations. Each automation tracks its own position independently.';

COMMENT ON COLUMN automation_progress.automation_name IS
    'Unique name of the automation.';

COMMENT ON COLUMN automation_progress.instance_id IS
    'Instance ID of the leader processing this automation.';

COMMENT ON COLUMN automation_progress.status IS
    'Status: ACTIVE, PAUSED, or FAILED.';

COMMENT ON COLUMN automation_progress.last_position IS
    'Last processed event position. Events with position > last_position will be processed.';

COMMENT ON COLUMN automation_progress.error_count IS
    'Number of consecutive errors. Automation is marked FAILED if it exceeds the configured threshold.';
