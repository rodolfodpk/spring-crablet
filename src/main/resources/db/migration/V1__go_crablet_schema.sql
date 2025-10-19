-- Agnostic event store for DCB, storing events of any type with TEXT[] tags and data.
-- Using transaction_id for proper ordering guarantees (see: https://event-driven.io/en/ordering_in_postgres_outbox/)

-- Create the default events table
CREATE TABLE events
(
    type           VARCHAR(64)              NOT NULL,
    tags           TEXT[]                   NOT NULL,
    data           JSON                     NOT NULL,
    transaction_id xid8                     NOT NULL,
    position       BIGSERIAL                NOT NULL PRIMARY KEY,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_event_type_length CHECK (LENGTH(type) <= 64)
);

-- Create the commands table for command tracking
CREATE TABLE commands
(
    transaction_id xid8                     NOT NULL PRIMARY KEY,
    type           VARCHAR(64)              NOT NULL,
    data           JSONB                    NOT NULL,
    metadata       JSONB,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Core indexes for essential operations
CREATE INDEX idx_events_transaction_position_btree ON events (transaction_id, position);
CREATE INDEX idx_events_tags ON events USING GIN (tags);
CREATE INDEX idx_events_type ON events (type);

-- Performance optimization: Composite index for DCB query pattern
-- Optimizes queries filtering by event type and ordering by position
-- Common pattern: SELECT * FROM events WHERE type = ANY(?) ORDER BY position ASC
CREATE INDEX idx_events_type_position ON events (type, position);

-- Function to batch insert events using UNNEST for better performance
-- Always uses 'events' table for maximum performance
CREATE OR REPLACE FUNCTION append_events_batch(
    p_types TEXT[],
    p_tags TEXT[], -- array of Postgres array literals as strings
    p_data JSONB[]
) RETURNS VOID AS
$$
BEGIN
    -- Insert directly into events table (no dynamic table name needed)
    INSERT INTO events (type, tags, data, transaction_id)
    SELECT t.type,
           t.tag_string::TEXT[], -- Cast the array literal string to TEXT[]
           t.data,
           pg_current_xact_id()
    FROM UNNEST($1, $2, $3) AS t(type, tag_string, data);
END;
$$ LANGUAGE plpgsql;

-- Optimized function for conditional event appending with DCB pattern
-- Uses position-based cursor checks for proper optimistic locking
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
