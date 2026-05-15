-- Step 2: maintain event_tags inside append_events_batch.
--
-- Every append now writes event_tags rows atomically in the same transaction
-- via a CTE on the events INSERT. The function signature is unchanged —
-- both call paths in EventStoreImpl (APPEND_EVENTS_IF_SQL and
-- APPEND_EVENTS_IF_CONNECTION_SQL) route through append_events_batch
-- and are covered without further changes.
--
-- Write amplification: sum(tags per event) additional rows per append.
-- Rollback path: drop event_tags (V7) and restore this function to its V5 form.

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
        INSERT INTO events (type, tags, data, transaction_id, occurred_at,
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
    INSERT INTO event_tags (position, key, value)
    SELECT i.position,
           split_part(tag, '=', 1)                      AS key,
           substring(tag FROM position('=' IN tag) + 1) AS value
    FROM inserted i,
         LATERAL unnest(i.tags) AS tag
    WHERE tag LIKE '%=%';
END;
$$ LANGUAGE plpgsql;
