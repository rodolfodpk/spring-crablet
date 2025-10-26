package com.com.com.com.com.wallets.testutils;

/**
 * Data class for workflow test scenarios.
 * Encapsulates workflow test data for parameterized tests.
 */
public record WorkflowScenario(
        String name,
        int fromBalance,
        int toBalance,
        int transferAmount
) {

    /**
     * Create a workflow scenario with validation.
     */
    public WorkflowScenario {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Scenario name cannot be null or empty");
        }
        if (fromBalance < 0) {
            throw new IllegalArgumentException("From balance cannot be negative");
        }
        if (toBalance < 0) {
            throw new IllegalArgumentException("To balance cannot be negative");
        }
        if (transferAmount <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
    }

    /**
     * Get the expected from balance after transfer.
     */
    public int expectedFromBalance() {
        return fromBalance - transferAmount;
    }

    /**
     * Get the expected to balance after transfer.
     */
    public int expectedToBalance() {
        return toBalance + transferAmount;
    }

    /**
     * Check if the transfer is valid (sufficient funds).
     */
    public boolean isValidTransfer() {
        return fromBalance >= transferAmount;
    }

    /**
     * Get the total balance before transfer.
     */
    public int totalBalanceBefore() {
        return fromBalance + toBalance;
    }

    /**
     * Get the total balance after transfer.
     */
    public int totalBalanceAfter() {
        return expectedFromBalance() + expectedToBalance();
    }

    /**
     * Check if money is conserved (total before = total after).
     */
    public boolean isMoneyConserved() {
        return totalBalanceBefore() == totalBalanceAfter();
    }

    /**
     * Get a description of the scenario.
     */
    public String description() {
        return String.format("%s: Transfer %d from balance %d to balance %d",
                name, transferAmount, fromBalance, toBalance);
    }

    /**
     * Get a short description for test display names.
     */
    public String shortDescription() {
        return String.format("%s (%d->%d, %d)",
                name, fromBalance, toBalance, transferAmount);
    }

    @Override
    public String toString() {
        return description();
    }
}
