-- Add idempotency_key to the commands table.
--
-- Enables pre-handler duplicate detection in CommandExecutorImpl without a
-- separate table or advisory lock. The executor inserts the command record
-- at the start of the transaction using pg_current_xact_id(); ON CONFLICT
-- on this partial unique index short-circuits duplicate submissions before
-- the handler runs.
--
-- Nullable: existing commands written without an idempotency key are unaffected.

ALTER TABLE commands ADD COLUMN idempotency_key TEXT;

CREATE UNIQUE INDEX idx_commands_idempotency_key
    ON commands (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
