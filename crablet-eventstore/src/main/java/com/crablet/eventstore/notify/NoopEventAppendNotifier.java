package com.crablet.eventstore.notify;

/**
 * Default no-op notifier used when notifications are disabled.
 */
public final class NoopEventAppendNotifier implements EventAppendNotifier {

    @Override
    public void notifyEventsAppended() {
    }
}
