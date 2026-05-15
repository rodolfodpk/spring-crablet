-- Derived tag index for efficient poller tag filtering.
--
-- event_tags is a normalized projection of events.tags maintained atomically
-- with every append. events.tags remains the canonical source of truth.
--
-- Purpose: per-processor poller tag filters (EventSelectionSqlBuilder) use indexed
-- EXISTS subqueries against event_tags instead of unnest(tags) LIKE scans.
--
-- append_events_if (idempotency and DCB conflict checks) continues to use
-- events.tags GIN — real decision models use 2+ tags per criterion, so a
-- single-tag B-tree fast path would benefit only a minority of commands while
-- adding write amplification for all appends.
--
-- This migration: table definition + backfill only. No query paths change here.
-- append_events_batch is updated in V8 to maintain event_tags on every new append.

CREATE TABLE event_tags
(
    position       BIGINT NOT NULL,
    key            TEXT   NOT NULL,
    value          TEXT   NOT NULL,
    PRIMARY KEY (key, value, position),
    CONSTRAINT fk_event_tags_position FOREIGN KEY (position) REFERENCES events(position) ON DELETE CASCADE
);

-- Covers poller position-range scans and required-key / exact-tag EXISTS subqueries.
CREATE INDEX idx_event_tags_position
    ON event_tags (position);

-- Backfill: populate event_tags from existing events.
-- ON CONFLICT DO NOTHING makes this idempotent — safe to re-run if interrupted.
-- Tags without '=' are skipped (defensive; the encoder always produces key=value strings).
-- split_part extracts the key; substring extracts everything after the first '=',
-- preserving any '=' characters in the value. Matches EventStoreImpl.parseTags behaviour.
INSERT INTO event_tags (position, key, value)
SELECT e.position,
       split_part(tag, '=', 1)                          AS key,
       substring(tag FROM position('=' IN tag) + 1)     AS value
FROM events e,
     LATERAL unnest(e.tags) AS tag
WHERE tag LIKE '%=%'
ON CONFLICT DO NOTHING;
