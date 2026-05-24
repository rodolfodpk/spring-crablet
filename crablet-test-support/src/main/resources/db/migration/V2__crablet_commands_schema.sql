-- Crablet command audit schema.
--
-- crablet_commands stores one row per executed command. It is linked to the
-- crablet_events table via transaction_id: both tables share the same
-- pg_current_xact_id() value when the command write and event appends happen
-- in the same database transaction.
-- There is no foreign key constraint - the join is intentionally via xid8 only.

CREATE TABLE crablet_commands
(
    command_id     UUID                     NOT NULL PRIMARY KEY,
    transaction_id xid8                     NOT NULL,
    type           TEXT                     NOT NULL,
    data           JSONB                    NOT NULL,
    metadata       JSONB,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_crablet_command_type_length CHECK (LENGTH(type) BETWEEN 1 AND 64)
);

CREATE UNIQUE INDEX idx_crablet_commands_transaction_id ON crablet_commands (transaction_id);

COMMENT ON TABLE crablet_commands IS
    'Command audit log. One row per executed command, linked to crablet_events via transaction_id.';

COMMENT ON COLUMN crablet_commands.transaction_id IS
    'PostgreSQL xid8 for the command execution transaction; joins to crablet_events.transaction_id for events produced by the command. Not a business transaction ID.';
