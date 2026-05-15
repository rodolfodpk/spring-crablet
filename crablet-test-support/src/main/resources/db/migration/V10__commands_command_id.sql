-- Replace transaction_id as PK with client-controlled command_id UUID.
-- Clients should generate UUID v7 (time-ordered, B-tree friendly) using any library.
-- Server fallback uses gen_random_uuid() (v4), which is acceptable for the
-- non-idempotent path because it needs no deduplication.
-- When the project upgrades to PostgreSQL 18, this can become gen_uuid_v7()
-- with no other design change. pg_uuidv7 can bridge the gap on PG 17 if
-- server-side v7 is ever needed here, but it is not required for correctness.
-- transaction_id is retained as a regular column for event-to-command linkage.

ALTER TABLE commands ADD COLUMN command_id UUID;
UPDATE commands SET command_id = gen_random_uuid() WHERE command_id IS NULL;
ALTER TABLE commands ALTER COLUMN command_id SET NOT NULL;
ALTER TABLE commands DROP CONSTRAINT commands_pkey;
ALTER TABLE commands ADD PRIMARY KEY (command_id);
CREATE INDEX idx_commands_transaction_id ON commands (transaction_id);
