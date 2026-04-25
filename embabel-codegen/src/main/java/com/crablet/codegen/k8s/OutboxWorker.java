package com.crablet.codegen.k8s;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * One outbox worker configuration derived from an {@code OutboxSpec} entry.
 */
public record OutboxWorker(String topic, String publisher, List<String> handles) {
    public OutboxWorker {
        requireNonNull(topic, "topic");
        requireNonNull(publisher, "publisher");
        handles = List.copyOf(requireNonNull(handles, "handles"));
    }
}
