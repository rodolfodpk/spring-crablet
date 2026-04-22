package com.crablet.eventpoller.internal.sharedfetch;

import com.crablet.eventstore.StoredEvent;

import java.util.List;
import java.util.stream.Gatherers;

final class StoredEventWindows {

    private StoredEventWindows() {}

    static List<List<StoredEvent>> fixed(List<StoredEvent> events, int size) {
        return events.stream()
                .gather(Gatherers.windowFixed(size))
                .toList();
    }

    static List<List<StoredEvent>> sliding(List<StoredEvent> events, int size) {
        return events.stream()
                .gather(Gatherers.windowSliding(size))
                .toList();
    }

    static List<List<StoredEvent>> adjacentPairs(List<StoredEvent> events) {
        return sliding(events, 2).stream()
                .filter(window -> window.size() == 2)
                .toList();
    }
}
