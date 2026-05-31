package com.example.loan.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubmitLoanApplicationScenarioTest {

    @Test
    @DisplayName("Submit loan application")
    void submitLoanApplication() {
        // Given: a loan application does not exist for APP-001

        // When: customer CUST-42 submits application APP-001 for 50000

        // Then: the system records LoanApplicationSubmitted
        // And: APP-001 appears in PendingLoanApplications
    }
}
