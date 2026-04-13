-- Add correlation and causation tracing columns to the events table.
--
-- correlation_id: ties together all events from the same business operation thread
--   (propagated from X-Correlation-ID HTTP header through automation chains).
-- causation_id:   the events.position of the event that directly triggered this one;
--   NULL for events produced by a direct user action.

ALTER TABLE events ADD COLUMN correlation_id UUID;
ALTER TABLE events ADD COLUMN causation_id  BIGINT;

-- Partial index: only index rows that have a correlation_id (most queries filter by it)
CREATE INDEX idx_events_correlation_id ON events (correlation_id)
    WHERE correlation_id IS NOT NULL;

-- Replace append_events_batch to store correlation/causation.
-- DEFAULT NULL means callers that omit the new params are unaffected.
CREATE OR REPLACE FUNCTION append_events_batch(
    p_types          TEXT[],
    p_tags           TEXT[],
    p_data           JSONB[],
    p_occurred_at    TIMESTAMP WITH TIME ZONE,
    p_correlation_id UUID    DEFAULT NULL,
    p_causation_id   BIGINT  DEFAULT NULL
) RETURNS VOID AS
$$
BEGIN
    INSERT INTO events (type, tags, data, transaction_id, occurred_at,
                        correlation_id, causation_id)
    SELECT t.type,
           t.tag_string::TEXT[],
           t.data,
           pg_current_xact_id(),
           p_occurred_at,
           p_correlation_id,
           p_causation_id
    FROM UNNEST($1, $2, $3) AS t(type, tag_string, data);
END;
$$ LANGUAGE plpgsql;

-- Replace append_events_if to pass correlation/causation through to append_events_batch.
-- Signature matches the existing 9-param version + 2 new DEFAULT NULL params.
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
    p_causation_id          BIGINT                   DEFAULT NULL
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
                ARRAY(SELECT unnest(p_idempotency_tags) ORDER BY 1),
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
                    SELECT 1 FROM events e
                    WHERE e.type = ANY(p_idempotency_types)
                      AND e.tags @> p_idempotency_tags
                    LIMIT 1
                )
            ELSE FALSE
        END,
        CASE
            WHEN p_event_types IS NULL AND p_condition_tags IS NULL AND p_after_cursor_position IS NULL THEN
                FALSE
            ELSE
                EXISTS (
                    SELECT 1 FROM events e
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

    RETURN jsonb_build_object(
        'success',        true,
        'message',        'events appended successfully',
        'events_count',   array_length(p_types, 1),
        'transaction_id', pg_current_xact_id()::TEXT
    );
END;
$$ LANGUAGE plpgsql;

COMMENT ON COLUMN events.correlation_id IS
    'Business operation thread ID — same value across all events caused by one user request, including automation-triggered downstream events.';

COMMENT ON COLUMN events.causation_id IS
    'Position (events.position) of the event that directly triggered this event. NULL for events caused by a direct user action.';
