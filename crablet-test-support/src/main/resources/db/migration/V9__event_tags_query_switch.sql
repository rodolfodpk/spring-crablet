-- Step 3: switch idempotency and DCB conflict checks to use event_tags.
--
-- For the common single-tag-pair case, both checks now query event_tags via
-- a B-tree index instead of scanning events.tags with GIN. The advisory lock
-- block in append_events_if is unchanged — only the query target changes.
--
-- Multi-tag cases fall back to the original events.tags @> checks to avoid
-- the GROUP BY/HAVING complexity of matching all pairs across event_tags rows.
--
-- Rollback path: restore this function to its V5 form.

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
        -- Idempotency check: single-tag path uses event_tags index; multi-tag falls back to GIN.
        CASE
            WHEN p_idempotency_types IS NOT NULL OR p_idempotency_tags IS NOT NULL THEN
                CASE
                    WHEN array_length(p_idempotency_tags, 1) = 1 THEN
                        EXISTS (
                            SELECT 1 FROM event_tags t
                            WHERE t.type = ANY(p_idempotency_types)
                              AND t.key   = split_part(p_idempotency_tags[1], '=', 1)
                              AND t.value = substring(p_idempotency_tags[1]
                                              FROM position('=' IN p_idempotency_tags[1]) + 1)
                            LIMIT 1
                        )
                    ELSE
                        EXISTS (
                            SELECT 1 FROM events e
                            WHERE e.type = ANY(p_idempotency_types)
                              AND e.tags @> p_idempotency_tags
                            LIMIT 1
                        )
                END
            ELSE FALSE
        END,

        -- DCB conflict check: single-tag path uses event_tags + position index; multi-tag falls back to GIN.
        CASE
            WHEN p_event_types IS NULL AND p_condition_tags IS NULL AND p_after_cursor_position IS NULL THEN
                FALSE
            WHEN p_condition_tags IS NOT NULL AND array_length(p_condition_tags, 1) = 1 THEN
                EXISTS (
                    SELECT 1
                    FROM event_tags t
                    JOIN events e ON e.position = t.position
                    WHERE t.position > p_after_cursor_position
                      AND (p_event_types IS NULL OR t.type = ANY(p_event_types))
                      AND t.key   = split_part(p_condition_tags[1], '=', 1)
                      AND t.value = substring(p_condition_tags[1]
                                      FROM position('=' IN p_condition_tags[1]) + 1)
                      AND e.transaction_id < pg_snapshot_xmin(pg_current_snapshot())
                    LIMIT 1
                )
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
