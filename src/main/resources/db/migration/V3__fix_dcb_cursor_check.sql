-- Fix DCB cursor violation bug
-- 
-- PROBLEM: The pg_snapshot_xmin filter prevents detecting concurrent modifications
-- from transactions that committed after the current transaction's snapshot began.
--
-- SOLUTION: Remove the pg_snapshot_xmin filter to check ALL committed events,
-- not just those visible in the current snapshot.
--
-- This ensures proper DCB optimistic locking: any event appended after the cursor
-- will cause a conflict, preventing lost updates.

CREATE OR REPLACE FUNCTION append_events_if(
    p_types TEXT[],
    p_tags TEXT[],
    p_data JSONB[],
    p_event_types TEXT[] DEFAULT NULL,
    p_condition_tags TEXT[] DEFAULT NULL,
    p_after_cursor_tx_id xid8 DEFAULT NULL,
    p_after_cursor_position BIGINT DEFAULT NULL
) RETURNS JSONB AS $$
DECLARE
    condition_count INTEGER;
    result JSONB;
BEGIN
    -- Initialize result
    result := '{"success": true, "message": "condition check passed"}'::JSONB;
    
    -- SINGLE ATOMIC DCB CHECK: Both cursor and condition checks in one query
    -- This ensures no race conditions between the two checks
    SELECT COUNT(*)
    INTO condition_count
    FROM events e
    WHERE (
        -- Condition check: event types and tags (if specified)
        (p_event_types IS NULL OR e.type = ANY(p_event_types))
        AND
        (p_condition_tags IS NULL OR e.tags @> p_condition_tags)
    )
    AND (
        -- Cursor check: events after the cursor (if cursor provided)
        p_after_cursor_tx_id IS NULL OR
        (e.transaction_id > p_after_cursor_tx_id) OR
        (e.transaction_id = p_after_cursor_tx_id AND e.position > p_after_cursor_position)
    )
    LIMIT 1;
    
    IF condition_count > 0 THEN
        -- Return generic violation - the client can determine the type
        -- This maintains atomicity by using only one query
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

