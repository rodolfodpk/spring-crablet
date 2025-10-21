# Add Fluent Builder API to Cursor

## Overview
Add a fluent builder API to `Cursor` following the same pattern as `QueryBuilder` and `AppendEvent.builder()`, making cursor creation more consistent with other Crablet APIs.

## Current API (Verbose)
```java
Cursor cursor = Cursor.of(
    lastEvent.position(),      
    lastEvent.occurredAt(),    
    lastEvent.transactionId()  
);
```

## New Fluent API

### Pattern 1: Build from scratch
```java
Cursor cursor = Cursor.builder()
    .position(42L)
    .occurredAt(Instant.now())
    .transactionId("12345")
    .build();
```

### Pattern 2: Build from StoredEvent (most common)
```java
Cursor cursor = Cursor.from(lastEvent).build();
```

### Pattern 3: Build from event with override
```java
Cursor cursor = Cursor.from(lastEvent)
    .transactionId("override-tx-id")
    .build();
```

## Implementation Details

### Add CursorBuilder class to Cursor.java

```java
public static class Builder {
    private SequenceNumber position;
    private Instant occurredAt;
    private String transactionId;
    
    private Builder() {
        // Defaults for optional fields
        this.position = SequenceNumber.zero();
        this.occurredAt = Instant.now();
        this.transactionId = "0";
    }
    
    private Builder(SequenceNumber position, Instant occurredAt, String transactionId) {
        this.position = position;
        this.occurredAt = occurredAt;
        this.transactionId = transactionId;
    }
    
    public Builder position(long position) {
        this.position = SequenceNumber.of(position);
        return this;
    }
    
    public Builder position(SequenceNumber position) {
        this.position = position;
        return this;
    }
    
    public Builder occurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
        return this;
    }
    
    public Builder transactionId(String transactionId) {
        this.transactionId = transactionId;
        return this;
    }
    
    public Cursor build() {
        return new Cursor(position, occurredAt, transactionId);
    }
}

public static Builder builder() {
    return new Builder();
}

public static Builder from(StoredEvent event) {
    return new Builder(
        SequenceNumber.of(event.position()),
        event.occurredAt(),
        event.transactionId()
    );
}
```

## Files to Modify

1. **src/main/java/com/crablet/core/Cursor.java**
   - Add `Builder` static nested class
   - Add `builder()` factory method
   - Add `from(StoredEvent)` factory method
   - Keep existing `of()` methods for backward compatibility

2. **Update documentation in docs/architecture/DCB_AND_CRABLET.md**
   - Change "Cursor Structure" section (lines 68-76) to show the fluent API:
   ```java
   // From StoredEvent (most common)
   Cursor cursor = Cursor.from(lastEvent).build();
   
   // Or build from scratch
   Cursor cursor = Cursor.builder()
       .position(42L)
       .occurredAt(Instant.now())
       .transactionId("12345")
       .build();
   ```
   - Update the Command Handler Pattern example to use the new fluent API:
   ```java
   // In the command handler example, change:
   AppendCondition condition = decisionModel
       .toAppendCondition(projection.cursor())
       .withIdempotencyCheck("DepositMade", "deposit_id", command.depositId())
       .build();
   ```

## Benefits

1. **Consistency**: Matches the pattern used by `QueryBuilder.create()` and `AppendEvent.builder()`
2. **Readability**: Clear, self-documenting code
3. **Flexibility**: Easy to override specific fields when building from events
4. **Convenience**: `from(StoredEvent)` handles the most common case elegantly
5. **Backward Compatible**: Keep all existing `of()` methods

## Migration Path

- **Old code continues to work**: All existing `Cursor.of()` methods remain
- **New code uses builder**: Gradually migrate to fluent API
- **Documentation updated**: Show the modern pattern in examples

## Testing

Run existing tests to ensure backward compatibility:
```bash
./mvnw test
```

All existing `Cursor.of()` usage will continue to work unchanged.

