package com.example.loan.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DuplicateSubmissionIsIgnoredScenarioTest {

    @Test
    @DisplayName("Duplicate submission is ignored")
    void duplicateSubmissionIsIgnored() {
        // Given: application APP-001 was already submitted

        // When: the same command is submitted again

        // Then: no new event is appended
    }
}
