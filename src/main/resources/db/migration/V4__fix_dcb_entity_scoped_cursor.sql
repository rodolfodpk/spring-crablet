-- Fix DCB to use proper entity-scoped cursor checks
-- 
-- PROBLEM: DCB cursor checks were too broad, checking for ANY events after cursor
-- instead of only events that would actually conflict with the operation.
--
-- SOLUTION: Use position-based cursor checks for proper DCB optimistic locking.
-- This ensures sequential operations work while preventing lost updates.

CREATE OR REPLACE FUNCTION append_events_if(
    p_types TEXT[],
    p_tags TEXT[],
    p_data JSONB[],
    p_event_types TEXT[] DEFAULT NULL,
    p_condition_tags TEXT[] DEFAULT NULL,
    p_after_cursor_position BIGINT DEFAULT NULL
) RETURNS JSONB AS
$$
DECLARE
    condition_count INTEGER;
    result          JSONB;
BEGIN
    -- Initialize result
    result := '{
      "success": true,
      "message": "condition check passed"
    }'::JSONB;

    -- ENTITY-SPECIFIC DCB CHECK: Check for conflicts with events affecting the same entities
    -- This ensures proper DCB optimistic locking without false positives from unrelated entities
    SELECT COUNT(*)
    INTO condition_count
    FROM events e
    WHERE (
        -- Condition check: event types and tags (if specified)
        (p_event_types IS NULL OR e.type = ANY (p_event_types))
            AND
        (p_condition_tags IS NULL OR e.tags @> p_condition_tags)
        )
      AND (
        -- Entity-specific cursor check: events after the cursor position (if cursor provided)
        -- Only check position for events that would actually conflict with the operation
        p_after_cursor_position IS NULL OR
        e.position > p_after_cursor_position
        )
    LIMIT 1;

    IF condition_count > 0 THEN
        -- Return generic violation - the client can determine the type
        result := jsonb_build_object(
                'success', false,
                'message', 'append condition violated',
                'matching_events_count', condition_count,
                'error_code', 'DCB_VIOLATION'
                  );
        RETURN result;
    END IF;

    -- If conditions pass, insert events using UNNEST for all cases
    PERFORM append_events_batch(p_types, p_tags, p_data);

    -- Return success status
    RETURN jsonb_build_object(
            'success', true,
            'message', 'events appended successfully',
            'events_count', array_length(p_types, 1)
           );
END;
$$ LANGUAGE plpgsql;
