-- Crablet event store schema for DCB-style event sourcing.
--
-- crablet_events.tags is the canonical tag storage used by command idempotency and DCB
-- conflict checks through GIN-backed containment queries.
--
-- crablet_event_tags is derived data maintained atomically on append. It exists to give
-- legacy per-processor poller SQL an indexed key/value lookup shape instead of
-- scanning unnest(crablet_events.tags) per candidate row.
--
-- Using transaction_id for proper ordering guarantees (see: https://event-driven.io/en/ordering_in_postgres_outbox/)
-- transaction_id is PostgreSQL's xid8 for the database transaction that appended the row.
-- It is shared by every event appended in the same transaction and links those events
-- to the command audit row (see crablet_commands table). It is not a business
-- transaction identifier such as deposit_id, withdrawal_id, or transfer_id.

CREATE TABLE crablet_events
(
    type           TEXT                     NOT NULL,
    tags           TEXT[]                   NOT NULL,
    data           JSONB                    NOT NULL,
    transaction_id xid8                     NOT NULL,
    position       BIGSERIAL                NOT NULL PRIMARY KEY,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id UUID,
    causation_id   BIGINT,
    CONSTRAINT chk_event_type_length CHECK (LENGTH(type) BETWEEN 1 AND 64)
);

CREATE TABLE crablet_event_tags
(
    position BIGINT NOT NULL,
    key      TEXT   NOT NULL,
    value    TEXT   NOT NULL,
    PRIMARY KEY (key, value, position),
    CONSTRAINT fk_crablet_event_tags_position FOREIGN KEY (position) REFERENCES crablet_events(position) ON DELETE CASCADE
);

CREATE INDEX idx_crablet_events_transaction_position_btree ON crablet_events (transaction_id, position);
CREATE INDEX idx_crablet_events_type_position ON crablet_events (type, position);
CREATE INDEX idx_crablet_events_tags_gin ON crablet_events USING GIN (tags);
CREATE INDEX idx_crablet_events_correlation_id ON crablet_events (correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE INDEX idx_crablet_event_tags_position ON crablet_event_tags (position);
CREATE INDEX idx_crablet_event_tags_key_position ON crablet_event_tags (key, position);

CREATE OR REPLACE FUNCTION append_events_batch(
    p_types          TEXT[],
    p_tags           TEXT[],
    p_data           JSONB[],
    p_occurred_at    TIMESTAMP WITH TIME ZONE,
    p_correlation_id UUID   DEFAULT NULL,
    p_causation_id   BIGINT DEFAULT NULL
) RETURNS VOID AS
$$
BEGIN
    WITH inserted AS (
        INSERT INTO crablet_events (type, tags, data, transaction_id, occurred_at,
                            correlation_id, causation_id)
        SELECT t.type,
               t.tag_string::TEXT[],
               t.data,
               pg_current_xact_id(),
               p_occurred_at,
               p_correlation_id,
               p_causation_id
        FROM UNNEST($1, $2, $3) AS t(type, tag_string, data)
        RETURNING position, tags
    )
    INSERT INTO crablet_event_tags (position, key, value)
    SELECT i.position,
           split_part(tag, '=', 1)                      AS key,
           substring(tag FROM position('=' IN tag) + 1) AS value
    FROM inserted i,
         LATERAL unnest(i.tags) AS tag
    WHERE tag LIKE '%=%';
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION append_events_if(
    p_types                 TEXT[],
    p_tags                  TEXT[],
    p_data                  JSONB[],
    p_event_types           TEXT[]                   DEFAULT NULL,
    p_condition_tags        TEXT[]                   DEFAULT NULL,
    p_after_cursor_position BIGINT                   DEFAULT NULL,
    p_idempotency_types     TEXT[]                   DEFAULT NULL,
    p_idempotency_tags      TEXT[]                   DEFAULT NULL,
    p_occurred_at           TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    p_correlation_id        UUID                     DEFAULT NULL,
    p_causation_id          BIGINT                   DEFAULT NULL,
    p_notify_channel        TEXT                     DEFAULT NULL,
    p_notify_payload        TEXT                     DEFAULT NULL
) RETURNS JSONB AS
$$
DECLARE
    v_has_duplicate BOOLEAN;
    v_has_conflict  BOOLEAN;
    v_lock_key      BIGINT;
BEGIN
    IF p_idempotency_types IS NOT NULL OR p_idempotency_tags IS NOT NULL THEN
        v_lock_key := hashtextextended(
            array_to_string(
                ARRAY(
                    SELECT 'type:' || item.value
                    FROM unnest(COALESCE(p_idempotency_types, ARRAY[]::TEXT[])) AS item(value)
                    UNION ALL
                    SELECT 'tag:' || item.value
                    FROM unnest(COALESCE(p_idempotency_tags, ARRAY[]::TEXT[])) AS item(value)
                    ORDER BY 1
                ),
                ','
            ),
            0
        );
        PERFORM pg_advisory_xact_lock(v_lock_key);
    END IF;

    SELECT
        CASE
            WHEN p_idempotency_types IS NOT NULL OR p_idempotency_tags IS NOT NULL THEN
                EXISTS (
                    SELECT 1 FROM crablet_events e
                    WHERE (p_idempotency_types IS NULL OR e.type = ANY(p_idempotency_types))
                      AND (p_idempotency_tags IS NULL OR e.tags @> p_idempotency_tags)
                    LIMIT 1
                )
            ELSE FALSE
        END,
        CASE
            WHEN p_event_types IS NULL AND p_condition_tags IS NULL AND p_after_cursor_position IS NULL THEN
                FALSE
            ELSE
                EXISTS (
                    SELECT 1 FROM crablet_events e
                    WHERE (p_event_types IS NULL OR e.type = ANY(p_event_types))
                      AND (p_condition_tags IS NULL OR e.tags @> p_condition_tags)
                      AND (p_after_cursor_position IS NULL OR e.position > p_after_cursor_position)
                      AND e.transaction_id < pg_snapshot_xmin(pg_current_snapshot())
                    LIMIT 1
                )
        END
    INTO v_has_duplicate, v_has_conflict;

    IF v_has_duplicate THEN
        RETURN jsonb_build_object(
            'success',    false,
            'message',    'duplicate operation detected',
            'error_code', 'IDEMPOTENCY_VIOLATION'
        );
    END IF;

    IF v_has_conflict THEN
        RETURN jsonb_build_object(
            'success',    false,
            'message',    'append condition violated',
            'error_code', 'DCB_VIOLATION'
        );
    END IF;

    PERFORM append_events_batch(
        p_types,
        p_tags,
        p_data,
        COALESCE(p_occurred_at, CURRENT_TIMESTAMP),
        p_correlation_id,
        p_causation_id
    );

    IF p_notify_channel IS NOT NULL THEN
        BEGIN
            PERFORM pg_notify(p_notify_channel, COALESCE(p_notify_payload, '*'));
        EXCEPTION WHEN OTHERS THEN
            RAISE WARNING 'pg_notify failed on channel %: %', p_notify_channel, SQLERRM;
        END;
    END IF;

    RETURN jsonb_build_object(
        'success',        true,
        'message',        'events appended successfully',
        'events_count',   array_length(p_types, 1),
        'transaction_id', pg_current_xact_id()::TEXT
    );
END;
$$ LANGUAGE plpgsql;

COMMENT ON TABLE crablet_events IS
    'Canonical Crablet event log. tags is the source of truth for event tags.';

COMMENT ON TABLE crablet_event_tags IS
    'Derived tag lookup table maintained atomically from crablet_events.tags for poller filtering.';

COMMENT ON COLUMN crablet_events.correlation_id IS
    'Business operation thread ID shared by all events caused by one user request, including automation-triggered downstream events.';

COMMENT ON COLUMN crablet_events.causation_id IS
    'Position (crablet_events.position) of the event that directly triggered this event. NULL for direct user actions.';

COMMENT ON COLUMN crablet_events.transaction_id IS
    'PostgreSQL xid8 for the transaction that appended this event; shared by all events appended in the same transaction and used to join command audit rows. Not a business transaction ID.';

COMMENT ON FUNCTION append_events_batch(TEXT[], TEXT[], JSONB[], TIMESTAMP WITH TIME ZONE, UUID, BIGINT) IS
    'Insert events with application-controlled timestamps and maintain derived crablet_event_tags rows.';

COMMENT ON FUNCTION append_events_if(TEXT[], TEXT[], JSONB[], TEXT[], TEXT[], BIGINT, TEXT[], TEXT[], TIMESTAMP WITH TIME ZONE, UUID, BIGINT, TEXT, TEXT) IS
    'Conditionally insert events using DCB conflict checks over canonical crablet_events.tags '
    'and optionally notify append listeners on commit. '
    'Advisory lock key derived via hashtextextended() → signed 64-bit integer (~2^64 possible '
    'values); collision probability per concurrent idempotency pair is negligible. Risk '
    'accepted: worst case is unnecessary lock serialization, not data corruption.';
