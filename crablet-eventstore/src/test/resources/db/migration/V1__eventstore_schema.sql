-- EventStore schema for DCB pattern
-- Stores events of any type with TEXT[] tags and data
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

-- Composite index for idempotency checks (type + tags GIN)
-- Optimizes: WHERE type = ANY(?) AND tags @> ?
-- This is the exact pattern used in idempotency checks
CREATE INDEX idx_events_type_tags_gin ON events (type, tags) 
    WHERE type IS NOT NULL AND tags IS NOT NULL;

-- Add GIN index to events for efficient tag queries
CREATE INDEX IF NOT EXISTS idx_events_tags_gin ON events USING GIN(tags);

-- Function to batch insert events using UNNEST for better performance
-- Always uses 'events' table for maximum performance
CREATE OR REPLACE FUNCTION append_events_batch(
    p_types TEXT[],
    p_tags TEXT[],
    p_data JSONB[],
    p_occurred_at TIMESTAMP WITH TIME ZONE
) RETURNS VOID AS
$$
BEGIN
    -- Insert directly into events table with provided timestamp
    INSERT INTO events (type, tags, data, transaction_id, occurred_at)
    SELECT t.type,
           t.tag_string::TEXT[], -- Cast the array literal string to TEXT[]
           t.data,
           pg_current_xact_id(),
           p_occurred_at  -- Use provided timestamp instead of CURRENT_TIMESTAMP
    FROM UNNEST($1, $2, $3) AS t(type, tag_string, data);
END;
$$ LANGUAGE plpgsql;

-- Optimized function for conditional event appending with DCB pattern
-- Uses advisory locks to prevent idempotency race conditions
CREATE OR REPLACE FUNCTION append_events_if(
    p_types TEXT[],
    p_tags TEXT[],
    p_data JSONB[],
    p_event_types TEXT[] DEFAULT NULL,
    p_condition_tags TEXT[] DEFAULT NULL,
    p_after_cursor_position BIGINT DEFAULT NULL,
    p_idempotency_types TEXT[] DEFAULT NULL,
    p_idempotency_tags TEXT[] DEFAULT NULL,
    p_occurred_at TIMESTAMP WITH TIME ZONE DEFAULT NULL
) RETURNS JSONB AS
$$
DECLARE
    v_has_duplicate BOOLEAN;
    v_has_conflict BOOLEAN;
    v_lock_key BIGINT;
BEGIN
    -- ADVISORY LOCK: Serialize duplicate checks per operation ID
    -- This prevents race conditions where concurrent transactions both see "no duplicate"
    IF p_idempotency_types IS NOT NULL OR p_idempotency_tags IS NOT NULL THEN
        -- Generate consistent lock key from operation tags
        v_lock_key := hashtextextended(
            array_to_string(
                ARRAY(SELECT unnest(p_idempotency_tags) ORDER BY 1),
                ','
            ),
            0
        );
        -- Acquire transaction-scoped advisory lock (blocks until available)
        PERFORM pg_advisory_xact_lock(v_lock_key);
    END IF;

    -- Perform checks (now protected by advisory lock)
    SELECT 
        -- Idempotency check: See ALL committed events
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
        
        -- Concurrency check: Only snapshot-visible events after cursor
        EXISTS (
            SELECT 1 FROM events e
            WHERE (p_event_types IS NULL OR e.type = ANY(p_event_types))
              AND (p_condition_tags IS NULL OR e.tags @> p_condition_tags)
              AND (p_after_cursor_position IS NULL OR e.position > p_after_cursor_position)
              AND e.transaction_id < pg_snapshot_xmin(pg_current_snapshot())
            LIMIT 1
        )
    INTO v_has_duplicate, v_has_conflict;

    -- Check idempotency first (most common failure)
    IF v_has_duplicate THEN
        RETURN jsonb_build_object(
            'success', false,
            'message', 'duplicate operation detected',
            'error_code', 'IDEMPOTENCY_VIOLATION'
        );
    END IF;

    -- Then check concurrency
    IF v_has_conflict THEN
        RETURN jsonb_build_object(
            'success', false,
            'message', 'append condition violated',
            'error_code', 'DCB_VIOLATION'
        );
    END IF;

    -- Both checks passed, insert events with provided timestamp
    -- Use provided timestamp or fall back to CURRENT_TIMESTAMP if not provided
    PERFORM append_events_batch(
        p_types, 
        p_tags, 
        p_data, 
        COALESCE(p_occurred_at, CURRENT_TIMESTAMP)
    );

    RETURN jsonb_build_object(
        'success', true,
        'message', 'events appended successfully',
        'events_count', array_length(p_types, 1)
    );
    
    -- Advisory lock is automatically released at transaction end
END;
$$ LANGUAGE plpgsql;

-- Comments explaining the functions
COMMENT ON FUNCTION append_events_batch(TEXT[], TEXT[], JSONB[], TIMESTAMP WITH TIME ZONE) IS 
'Insert events with application-controlled timestamps for deterministic testing';

COMMENT ON FUNCTION append_events_if(TEXT[], TEXT[], JSONB[], TEXT[], TEXT[], BIGINT, TEXT[], TEXT[], TIMESTAMP WITH TIME ZONE) IS 
'Conditionally insert events with application-controlled timestamps for deterministic testing';

