# Database Schema

## Overview

Crablet uses PostgreSQL with two main tables (`events` and `commands`), one outbox table (`outbox_topic_progress`), and two PL/pgSQL functions for inserting events.

## Tables

### events

Stores all events in a single table with tag-based identification.

```sql
CREATE TABLE events
(
    type           TEXT                     NOT NULL,
    tags           TEXT[]                   NOT NULL,
    data           JSONB                    NOT NULL,
    transaction_id xid8                     NOT NULL,
    position       BIGSERIAL                NOT NULL PRIMARY KEY,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id UUID,
    causation_id   BIGINT,
    CONSTRAINT chk_event_type_length CHECK (LENGTH(type) <= 64)
);
```

**Columns:**
- `type` - Event type name (e.g., "WalletOpened", "DepositMade")
- `tags` - Array of key=value tags for querying (e.g., `{"wallet_id=123", "deposit_id=456"}`)
- `data` - JSONB event payload. Crablet treats event data as immutable application data; JSONB keeps it queryable and indexable for diagnostics, backfills, and projections without preserving insignificant input formatting.
- `transaction_id` - PostgreSQL transaction ID (`xid8`) for the database transaction that appended the event. Every event appended in the same transaction has the same value, and command audit rows join to events through this value. This is not a business transaction ID such as a deposit, withdrawal, transfer, or order ID.
- `position` - Auto-incrementing sequence number (primary key)
- `occurred_at` - Event timestamp
- `correlation_id` - Optional UUID used to correlate related operations
- `causation_id` - Optional event position that caused this event

### commands

Stores commands for audit trail and debugging.

```sql
CREATE TABLE commands
(
    command_id     UUID                     NOT NULL PRIMARY KEY,
    transaction_id xid8                     NOT NULL,
    type           TEXT                     NOT NULL,
    data           JSONB                    NOT NULL,
    metadata       JSONB,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_command_type_length CHECK (LENGTH(type) <= 64)
);
```

`command_id` is the command identity and idempotency key. `transaction_id` is the audit join key to
`events.transaction_id`; it is unique so one command transaction maps unambiguously to the events it
produced. `transaction_id` is not an application/business transaction ID.

### outbox_topic_progress

Tracks event publishing progress per topic and publisher for the Outbox.

```sql
CREATE TABLE outbox_topic_progress (
    topic              TEXT                        NOT NULL,
    publisher           TEXT                        NOT NULL,
    last_position      BIGINT                      NOT NULL DEFAULT 0,
    last_published_at  TIMESTAMP WITH TIME ZONE,
    status             TEXT                        NOT NULL DEFAULT 'ACTIVE',
    error_count        INT                         NOT NULL DEFAULT 0,
    last_error         TEXT,
    updated_at         TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    leader_instance    TEXT,
    leader_since       TIMESTAMP WITH TIME ZONE,
    leader_heartbeat   TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT pk_outbox_topic_progress PRIMARY KEY (topic, publisher),
    CONSTRAINT chk_outbox_topic_length CHECK (LENGTH(topic) <= 128),
    CONSTRAINT chk_outbox_publisher_length CHECK (LENGTH(publisher) <= 128),
    CONSTRAINT chk_outbox_leader_instance_length CHECK (leader_instance IS NULL OR LENGTH(leader_instance) <= 256),
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'PAUSED', 'FAILED'))
);
```

**Columns:**
- `topic` - Topic name for event filtering (part of composite primary key)
- `publisher` - Publisher name (part of composite primary key)
- `last_position` - Last published event position in the event stream
- `last_published_at` - Timestamp of last successful publish
- `status` - Publisher status: 'ACTIVE', 'PAUSED', or 'FAILED'
- `error_count` - Number of consecutive errors
- `last_error` - Last error message
- `updated_at` - Last update timestamp
- `leader_instance` - Hostname/pod name of the instance holding the lock
- `leader_since` - When the current instance became the leader
- `leader_heartbeat` - Last heartbeat timestamp for liveness detection

Identifier limits are explicit `CHECK` constraints rather than `VARCHAR(n)`: event and command types are capped at 64 characters, outbox topics and publishers at 128, view and automation names at 256, instance IDs at 256, module names at 64, and shared-fetch processor IDs at 320. The shared-fetch processor limit allows outbox's `topic:publisher` serialized key while keeping indexed values bounded.

**Indexes:**
```sql
CREATE INDEX idx_topic_status ON outbox_topic_progress(topic, status);
CREATE INDEX idx_topic_leader ON outbox_topic_progress(topic, leader_instance);
CREATE INDEX idx_topic_publisher_heartbeat ON outbox_topic_progress(topic, publisher, leader_heartbeat);
```

## Indexes

### Core Indexes

```sql
-- PostgreSQL transaction and position ordering
CREATE INDEX idx_events_transaction_position_btree ON events (transaction_id, position);

-- Command-to-event audit join
CREATE UNIQUE INDEX idx_commands_transaction_id ON commands (transaction_id);

-- Tag-based queries (GIN index for array containment)
CREATE INDEX idx_events_tags_gin ON events USING GIN (tags);

-- Event type filtering
CREATE INDEX idx_events_type_position ON events (type, position);
```

### Optimized Indexes

```sql
-- DCB query pattern: filter by type, order by position
CREATE INDEX idx_events_type_position ON events (type, position);
```

## Functions

### append_events_batch()

Batch inserts events without DCB checks.

```sql
CREATE OR REPLACE FUNCTION append_events_batch(
    p_types TEXT[],
    p_tags TEXT[],
    p_data JSONB[],
    p_occurred_at TIMESTAMP WITH TIME ZONE
) RETURNS VOID
```

Used internally by `EventStore.appendCommutative()` (via the package-private `append()` method) for simple event insertion. Supports application-controlled timestamps for deterministic testing.

### append_events_if()

Inserts events with DCB conflict detection and idempotency checks.

```sql
CREATE OR REPLACE FUNCTION append_events_if(
    p_types TEXT[],
    p_tags TEXT[],
    p_data JSONB[],
    p_event_types TEXT[] DEFAULT NULL,
    p_condition_tags TEXT[] DEFAULT NULL,
    p_after_position BIGINT DEFAULT NULL,
    p_idempotency_types TEXT[] DEFAULT NULL,
    p_idempotency_tags TEXT[] DEFAULT NULL,
    p_occurred_at TIMESTAMP WITH TIME ZONE DEFAULT NULL
) RETURNS JSONB
```

**Parameters:**
- `p_types`, `p_tags`, `p_data` - Events to insert
- `p_event_types`, `p_condition_tags` - Decision model query (for DCB conflict check)
- `p_after_position` - Stream position (check events after this)
- `p_idempotency_types`, `p_idempotency_tags` - Idempotency query (check all events)

**Returns:**
```json
{
  "success": true,
  "message": "events appended successfully",
  "events_count": 1
}
```

Or on conflict/duplicate:
```json
{
  "success": false,
  "message": "duplicate operation detected",
  "error_code": "IDEMPOTENCY_VIOLATION"
}
```

**Advisory Locks:**
- Uses `pg_advisory_xact_lock()` to serialize idempotency checks
- Prevents race conditions where two transactions both see "no duplicate"
- Lock is automatically released at transaction end

**Why Advisory Locks Are Needed for Idempotency:**

Unlike streamPosition-based concurrency checks, idempotency checks cannot rely on PostgreSQL's snapshot isolation because there's no prior state (stream position) to check against. The race condition occurs when:

1. Transaction A checks "does entity exist?" → No → Proceeds to create
2. Transaction B checks "does entity exist?" → No (A hasn't committed yet) → Also proceeds to create
3. Result: Both transactions create the entity → Duplicate ❌

Snapshot isolation doesn't help here because both transactions see the same "entity doesn't exist" state. Advisory locks serialize the duplicate check, ensuring only one transaction can check "does entity exist?" at a time, preventing both from seeing "no duplicate" simultaneously.

**StreamPosition-Based Checks Don't Need Locks:**
- StreamPosition-based checks query "has anything changed AFTER stream position X?"
- Snapshot isolation handles this: if transaction A writes at position 43, transaction B will see position 43 when it tries to write and detect the conflict
- No advisory locks needed - PostgreSQL's MVCC (Multi-Version Concurrency Control) provides the protection

**Concurrency Check:**
- Checks events AFTER stream position
- Only sees snapshot-visible committed events
- Used by DCB for optimistic locking

**Idempotency Check:**
- Checks ALL events (ignores stream position)
- Finds duplicate operations by operation ID tags
- Example: checking if `withdrawal_id:456` already exists

## Setup

### Using Flyway

Place the schema SQL in `src/main/resources/db/migration/V1__initial_schema.sql`:

```sql
-- Copy content from crablet-eventstore/src/test/resources/db/migration/V1__eventstore_schema.sql
```

### Manual Setup

```bash
psql -U postgres -d your_database -f schema.sql
```

### Spring Boot Configuration

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/your_database
spring.datasource.username=your_user
spring.datasource.password=your_password
spring.flyway.enabled=true
```

## Tag Format

Tags are stored as `TEXT[]` in PostgreSQL with format `key=value` (using equals sign, not colon):

```java
AppendEvent.builder("WalletOpened")
    .tag("wallet_id", "wallet-123")      // Stored as "wallet_id=wallet-123"
    .tag("owner_id", "user-456")         // Stored as "owner_id=user-456"
    .build();
```

**Querying tags:**
```sql
-- Find events with wallet_id using LIKE pattern
SELECT * FROM events WHERE EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE t LIKE 'wallet_id=%');

-- Find events with exact tag match
SELECT * FROM events WHERE tags @> ARRAY['wallet_id=wallet-123'];

-- Find events with multiple tags
SELECT * FROM events WHERE tags @> ARRAY['wallet_id=wallet-123', 'event_type=deposit'];
```
