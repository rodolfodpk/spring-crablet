-- Derived tag index for efficient tag-based queries.
--
-- event_tags is a normalized projection of events.tags maintained atomically
-- with every append. events remains the canonical source of truth.
--
-- Benefits:
--   - Per-processor poller tag filters use B-tree indexes instead of unnest(tags) LIKE scans.
--   - Idempotency and DCB conflict checks avoid GIN scans on events.tags for the common
--     single-tag-pair case.
--
-- This migration: table definition + backfill only. No query paths change here.
-- append_events_batch is updated in V8 to maintain event_tags on every new append.

CREATE TABLE event_tags
(
    position       BIGINT      NOT NULL,
    transaction_id xid8        NOT NULL,
    type           VARCHAR(64) NOT NULL,
    key            TEXT        NOT NULL,
    value          TEXT        NOT NULL,
    PRIMARY KEY (key, value, position)
);

-- Supports position-range scans used by the per-processor poller.
CREATE INDEX idx_event_tags_position
    ON event_tags (position);

-- Covers queries that filter by event type + tag key + tag value + position range.
-- Used by idempotency checks and DCB conflict checks after step 3 query switch.
CREATE INDEX idx_event_tags_type_key_value_position
    ON event_tags (type, key, value, position);

-- Supports joins between commands and event_tags via transaction_id.
CREATE INDEX idx_event_tags_transaction_id
    ON event_tags (transaction_id);

-- Backfill: populate event_tags from existing events.
-- ON CONFLICT DO NOTHING makes this idempotent — safe to re-run if interrupted.
-- Tags without '=' are skipped (defensive; the encoder always produces key=value strings).
-- split_part extracts the key; substring extracts everything after the first '=',
-- preserving any '=' characters in the value. Matches EventStoreImpl.parseTags behaviour.
INSERT INTO event_tags (position, transaction_id, type, key, value)
SELECT e.position,
       e.transaction_id,
       e.type,
       split_part(tag, '=', 1)                          AS key,
       substring(tag FROM position('=' IN tag) + 1)     AS value
FROM events e,
     LATERAL unnest(e.tags) AS tag
WHERE tag LIKE '%=%'
ON CONFLICT DO NOTHING;
