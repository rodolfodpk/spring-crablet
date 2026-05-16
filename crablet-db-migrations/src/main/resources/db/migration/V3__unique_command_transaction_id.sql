-- A command audit row is written in the same PostgreSQL transaction as the
-- events produced by that command. Enforce one command row per transaction so
-- commands.transaction_id remains an unambiguous audit join key to events.

DROP INDEX IF EXISTS idx_commands_transaction_id;

CREATE UNIQUE INDEX idx_commands_transaction_id
    ON commands (transaction_id);
