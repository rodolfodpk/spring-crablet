# Part 6: Outbox

This tutorial introduces `crablet-outbox`.

Canonical compile fixture:
[Part 6 compile fixture](../../docs-samples/src/main/java/com/crablet/docs/samples/tutorial/Tutorial06OutboxSample.java)

## Why This Part Exists

Automations are one way to react to events inside your application.

Outbox solves a different problem:

- reliably publishing events to external systems
- without breaking transactional consistency between your write model and integration side effects

Skip this part if your application does not publish domain events to external systems.

You will learn:

- how to publish integration events reliably
- how topics and publishers are configured
- how outbox processing relates to the event poller

## Enable Outbox

```properties
crablet.outbox.enabled=true
crablet.outbox.polling-interval-ms=1000
crablet.outbox.batch-size=100
crablet.outbox.topics.default.publishers=KafkaPublisher,LogPublisher
```

`crablet.outbox.*` is the global module config. These values are defaults for the outbox module as a whole.

## Shared-Fetch Mode

By default each outbox `(topic, publisher)` processor runs its own DB query per polling cycle. If you have many processors and want to reduce DB load on LISTEN/NOTIFY wakeups, enable the shared-fetch path:

```properties
crablet.outbox.shared-fetch.enabled=true
crablet.outbox.fetch-batch-size=1000
```

Shared-fetch uses one position-only DB fetch per module cycle, then routes events to each outbox processor in memory. `fetch-batch-size` controls the shared DB read size. `batch-size` still controls how many matched events each `(topic, publisher)` processor publishes per cycle.

Shared-fetch requires the scan-progress tables from the V14-style migration used by the example app. Leave the flag unset or `false` if your application has not added those tables.

## Define A Publisher

```java
@Component
public class KafkaPublisher implements OutboxPublisher {

    private final KafkaTemplate<String, StoredEvent> kafkaTemplate;

    public KafkaPublisher(KafkaTemplate<String, StoredEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishBatch(List<StoredEvent> events) {
        for (StoredEvent event : events) {
            kafkaTemplate.send("default", event);
        }
    }

    @Override
    public String getName() {
        return "KafkaPublisher";
    }

    @Override
    public boolean isHealthy() {
        return true;
    }
}
```

Outbox still runs per poller instance. In practice that usually means one processor per `(topic, publisher)` pair, with optional per-publisher overrides on top of the global defaults.

That separation is why publishing can fail and retry independently without losing the original committed business event.

## Deployment Guidance

Outbox is also built on `crablet-event-poller`.

Recommended production shape:

- run **1 application instance per cluster** for the simplest topology
- if outbox needs isolation, run one singleton outbox worker service

Adding many replicas does not increase throughput for a given `(topic, publisher)` processor set. One leader remains active, the others wait in standby.

## Checkpoint

After this part, you should understand what the outbox adds on top of plain event storage:

- domain events are still written transactionally
- external publication happens asynchronously
- publisher progress is tracked independently per processor

Expected result:

- appending domain events makes them eligible for outbox publication
- configured publishers receive those events asynchronously
- failures can be retried without losing the original committed domain events

## Finish

You now have the intended learning path:

1. append and project events
2. wrap writes in commands
3. protect state-dependent decisions with DCB
4. project views
5. react with automations
6. publish externally with outbox
