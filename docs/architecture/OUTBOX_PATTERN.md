# Outbox Pattern Implementation

## Overview

Transactional outbox pattern for reliable event publishing to external systems.

## Guarantees

- At-least-once delivery (events may be published multiple times)
- Global ordering (events published in exact position order)
- Transactional consistency (outbox updated atomically with events)

## Architecture

### Components

1. **Publisher Progress Table**: Tracks last published position per publisher
2. **Processor**: Polls events after last position, publishes via configured publishers
3. **Publishers**: Abstract interface for Kafka, NATS, webhooks, etc

### Publishing Flow

1. Event appended to events table
2. Background processor polls events after publisher's last position (every 1s)
3. Processor fetches events (WHERE position > last_position)
4. Processor publishes batch to all configured publishers
5. Processor updates publisher's last_position

## Configuration

```properties
# Enable outbox processing
crablet.outbox.enabled=true

# Batch size for polling
crablet.outbox.batch-size=100

# Polling interval (milliseconds)
crablet.outbox.polling-interval-ms=1000

# Max retries before marking as FAILED
crablet.outbox.max-retries=3

# Enable log publisher (for testing)
crablet.outbox.publishers.log.enabled=true
```

## Transaction Isolation

The outbox processor uses **READ COMMITTED** isolation level (PostgreSQL default).

**Why READ COMMITTED is sufficient:**
1. Advisory locks in `append_events_if` serialize event appends per stream
2. Position values are gap-free due to serialized inserts
3. Processor polls every 1 second (eventual consistency is acceptable)
4. Even if a transaction delays commit, processor sees it on next poll

**No gap problem:** Since `append_events_if` uses `pg_advisory_xact_lock()`, concurrent appends are serialized, ensuring position values increment without gaps. This guarantees global ordering.

**Pause/Resume Publishers:**

Via REST API:
```bash
# Get all publisher statuses
curl http://localhost:8080/api/outbox/publishers

# Get specific publisher status
curl http://localhost:8080/api/outbox/publishers/LogPublisher

# Pause a publisher
curl -X POST http://localhost:8080/api/outbox/publishers/LogPublisher/pause

# Resume a publisher
curl -X POST http://localhost:8080/api/outbox/publishers/LogPublisher/resume

# Reset a failed publisher
curl -X POST http://localhost:8080/api/outbox/publishers/LogPublisher/reset

# Get publisher lag
curl http://localhost:8080/api/outbox/publishers/lag
```

Via SQL (direct database access):
```sql
-- Pause a publisher (stops processing)
UPDATE outbox_publisher_progress 
SET status = 'PAUSED' 
WHERE publisher_name = 'LogPublisher';

-- Resume a publisher
UPDATE outbox_publisher_progress 
SET status = 'ACTIVE' 
WHERE publisher_name = 'LogPublisher';

-- Reset a failed publisher
UPDATE outbox_publisher_progress 
SET status = 'ACTIVE', 
    error_count = 0, 
    last_error = NULL 
WHERE publisher_name = 'LogPublisher';
```

## Custom Publishers

Implement `OutboxPublisher` interface:

```java
@Component
public class KafkaPublisher implements OutboxPublisher {
    
    @Override
    public void publishBatch(List<StoredEvent> events) throws PublishException {
        // Publish to Kafka
    }
    
    @Override
    public String getName() {
        return "KafkaPublisher";
    }
    
    @Override
    public boolean isHealthy() {
        return kafkaTemplate.isHealthy();
    }
}
```

## Management Service

The `OutboxManagementService` provides high-level operations for managing publishers:

### Service Operations

- `pausePublisher(String publisherName)` - Pause a publisher
- `resumePublisher(String publisherName)` - Resume a publisher  
- `resetPublisher(String publisherName)` - Reset a failed publisher
- `getPublisherStatus(String publisherName)` - Get status of specific publisher
- `getAllPublisherStatus()` - Get status of all publishers
- `getPublisherLag()` - Get lag information for all publishers
- `publisherExists(String publisherName)` - Check if publisher exists

### REST API Endpoints

- `GET /api/outbox/publishers` - List all publishers
- `GET /api/outbox/publishers/{name}` - Get specific publisher status
- `POST /api/outbox/publishers/{name}/pause` - Pause publisher
- `POST /api/outbox/publishers/{name}/resume` - Resume publisher
- `POST /api/outbox/publishers/{name}/reset` - Reset publisher
- `GET /api/outbox/publishers/lag` - Get publisher lag

## Monitoring

- Publisher progress: `SELECT * FROM outbox_publisher_progress`
- Failed publishers: `SELECT * FROM outbox_publisher_progress WHERE status = 'FAILED'`
- Publisher lag: `SELECT publisher_name, last_position, MAX(position) as current_position FROM outbox_publisher_progress, events GROUP BY publisher_name, last_position`
