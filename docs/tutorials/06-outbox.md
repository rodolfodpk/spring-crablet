# Part 6: Outbox

This tutorial introduces `crablet-outbox`.

You will learn:

- how to publish integration events reliably
- how topics and publishers are configured
- how outbox processing relates to the event poller

## Enable Outbox

```properties
crablet.outbox.enabled=true
crablet.outbox.polling-interval-ms=1000
crablet.outbox.topics.default.publishers=KafkaPublisher,LogPublisher
```

`crablet.outbox.*` is the global module config. These values are defaults for the outbox module as a whole.

## Define A Publisher

```java
@Component
public class KafkaPublisher implements OutboxPublisher {

    private final KafkaTemplate<String, StoredEvent> kafkaTemplate;

    public KafkaPublisher(KafkaTemplate<String, StoredEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(String topic, List<StoredEvent> events) {
        for (StoredEvent event : events) {
            kafkaTemplate.send(topic, event);
        }
    }

    @Override
    public String getName() {
        return "KafkaPublisher";
    }
}
```

Outbox still runs per poller instance. In practice that usually means one processor per `(topic, publisher)` pair, with optional per-publisher overrides on top of the global defaults.

## Deployment Guidance

Outbox is also built on `crablet-event-poller`.

Recommended production shape:

- run **1 instance** for the simple default
- run **2 instances at most** for active/failover behavior

Adding many replicas does not increase throughput for a given `(topic, publisher)` processor set. One leader remains active, the others wait to take over on failure.

## Finish

You now have the intended learning path:

1. append and project events
2. wrap writes in commands
3. protect state-dependent decisions with DCB
4. project views
5. react with automations
6. publish externally with outbox
