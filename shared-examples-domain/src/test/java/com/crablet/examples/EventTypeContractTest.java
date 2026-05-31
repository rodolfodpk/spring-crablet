package com.crablet.examples;

import com.crablet.examples.course.events.CourseEvent;
import com.crablet.examples.wallet.events.WalletEvent;
import com.crablet.test.EventTypeContract;
import org.junit.jupiter.api.Test;

class EventTypeContractTest {

    @Test
    void walletJsonSubTypeNamesShouldMatchEventTypeNames() {
        EventTypeContract.assertJsonSubTypesMatchEventType(WalletEvent.class);
    }

    @Test
    void courseJsonSubTypeNamesShouldMatchEventTypeNames() {
        EventTypeContract.assertJsonSubTypesMatchEventType(CourseEvent.class);
    }
}
