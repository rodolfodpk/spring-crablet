-- Scan progress tables for shared-fetch mode (crablet.views.shared-fetch.enabled=true).
-- Required only when shared-fetch is enabled; not touched by the legacy per-processor path.

CREATE TABLE crablet_module_scan_progress (
    module_name   TEXT   PRIMARY KEY,
    scan_position BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE crablet_processor_scan_progress (
    module_name      TEXT   NOT NULL,
    processor_id     TEXT   NOT NULL,
    scanned_position BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (module_name, processor_id)
);
