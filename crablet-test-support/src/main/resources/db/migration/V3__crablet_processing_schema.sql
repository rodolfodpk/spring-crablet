-- Crablet processing progress schema.
--
-- These tables are framework-owned cursors and status records for outbox,
-- views, automations, and shared-fetch module scans.

CREATE TABLE crablet_outbox_topic_progress
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

    CONSTRAINT pk_crablet_outbox_topic_progress PRIMARY KEY (topic, publisher),
    CONSTRAINT chk_crablet_outbox_status CHECK (status IN ('ACTIVE', 'PAUSED', 'FAILED')),
    CONSTRAINT chk_crablet_outbox_topic_len CHECK (length(topic) <= 128),
    CONSTRAINT chk_crablet_outbox_publisher_len CHECK (length(publisher) <= 128),
    CONSTRAINT chk_crablet_outbox_leader_instance_len CHECK (leader_instance IS NULL OR length(leader_instance) <= 256)
);

CREATE TABLE crablet_view_progress
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
    CONSTRAINT chk_crablet_view_status CHECK (status IN ('ACTIVE', 'PAUSED', 'FAILED')),
    CONSTRAINT chk_crablet_view_name_len CHECK (length(view_name) <= 256),
    CONSTRAINT chk_crablet_view_instance_len CHECK (instance_id IS NULL OR length(instance_id) <= 256)
);

CREATE TABLE crablet_automation_progress
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
    CONSTRAINT chk_crablet_automation_status CHECK (status IN ('ACTIVE', 'PAUSED', 'FAILED')),
    CONSTRAINT chk_crablet_automation_name_len CHECK (length(automation_name) <= 256),
    CONSTRAINT chk_crablet_automation_instance_len CHECK (instance_id IS NULL OR length(instance_id) <= 256)
);

CREATE TABLE crablet_module_scan_progress
(
    module_name   TEXT   PRIMARY KEY,
    scan_position BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_crablet_module_scan_module_name_len CHECK (length(module_name) <= 64)
);

CREATE TABLE crablet_processor_scan_progress
(
    module_name      TEXT   NOT NULL,
    processor_id     TEXT   NOT NULL,
    scanned_position BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (module_name, processor_id),
    CONSTRAINT chk_crablet_processor_scan_module_name_len CHECK (length(module_name) <= 64),
    CONSTRAINT chk_crablet_processor_scan_processor_id_len CHECK (length(processor_id) <= 320)
);

CREATE INDEX idx_crablet_outbox_topic_status ON crablet_outbox_topic_progress (topic, status);
CREATE INDEX idx_crablet_outbox_topic_leader ON crablet_outbox_topic_progress (topic, leader_instance);
CREATE INDEX idx_crablet_outbox_topic_publisher_heartbeat ON crablet_outbox_topic_progress (topic, publisher, leader_heartbeat);

CREATE INDEX idx_crablet_view_progress_status ON crablet_view_progress (status);
CREATE INDEX idx_crablet_view_progress_instance ON crablet_view_progress (instance_id);
CREATE INDEX idx_crablet_view_progress_last_updated ON crablet_view_progress (last_updated_at);

CREATE INDEX idx_crablet_automation_progress_status ON crablet_automation_progress (status);
CREATE INDEX idx_crablet_automation_progress_instance ON crablet_automation_progress (instance_id);
CREATE INDEX idx_crablet_automation_progress_last_updated ON crablet_automation_progress (last_updated_at);

COMMENT ON TABLE crablet_outbox_topic_progress IS
    'Tracks last published event position per publisher per topic. Each publisher advances independently through events matching its topic criteria.';

COMMENT ON COLUMN crablet_outbox_topic_progress.leader_instance IS
    'Hostname/pod name of the instance currently holding the lock for this topic-publisher pair.';

COMMENT ON COLUMN crablet_outbox_topic_progress.leader_since IS
    'When the current instance became the leader.';

COMMENT ON COLUMN crablet_outbox_topic_progress.leader_heartbeat IS
    'Last heartbeat timestamp from the leader instance. Used to detect abandoned pairs when leader crashes.';

COMMENT ON TABLE crablet_view_progress IS
    'Progress tracking for view projections. Each view tracks its own position independently.';

COMMENT ON COLUMN crablet_view_progress.view_name IS
    'Unique name of the view projection.';

COMMENT ON COLUMN crablet_view_progress.instance_id IS
    'Instance ID of the leader processing this view.';

COMMENT ON COLUMN crablet_view_progress.status IS
    'Status: ACTIVE, PAUSED, or FAILED.';

COMMENT ON COLUMN crablet_view_progress.last_position IS
    'Last processed event position. Events with position > last_position will be processed.';

COMMENT ON COLUMN crablet_view_progress.error_count IS
    'Number of consecutive errors. View is marked FAILED if it exceeds the configured threshold.';

COMMENT ON TABLE crablet_automation_progress IS
    'Progress tracking for event-driven automations. Each automation tracks its own position independently.';

COMMENT ON COLUMN crablet_automation_progress.automation_name IS
    'Unique name of the automation.';

COMMENT ON COLUMN crablet_automation_progress.instance_id IS
    'Instance ID of the leader processing this automation.';

COMMENT ON COLUMN crablet_automation_progress.status IS
    'Status: ACTIVE, PAUSED, or FAILED.';

COMMENT ON COLUMN crablet_automation_progress.last_position IS
    'Last processed event position. Events with position > last_position will be processed.';

COMMENT ON COLUMN crablet_automation_progress.error_count IS
    'Number of consecutive errors. Automation is marked FAILED if it exceeds the configured threshold.';
