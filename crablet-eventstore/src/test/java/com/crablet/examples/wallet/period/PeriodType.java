package com.crablet.examples.wallet.period;

/**
 * Enum representing different period types for wallet statement segmentation.
 * Used in the closing the books pattern to segment wallet events by time periods.
 */
public enum PeriodType {
    /**
     * Hourly statements - creates a new statement period each hour.
     * Statement ID format: wallet:{walletId}:{year}-{month}-{day}-{hour}
     */
    HOURLY,
    
    /**
     * Daily statements - creates a new statement period each day.
     * Statement ID format: wallet:{walletId}:{year}-{month}-{day}
     */
    DAILY,
    
    /**
     * Monthly statements - creates a new statement period each month.
     * Statement ID format: wallet:{walletId}:{year}-{month}
     * This is the default period type.
     */
    MONTHLY,
    
    /**
     * Yearly statements - creates a new statement period each year.
     * Statement ID format: wallet:{walletId}:{year}
     */
    YEARLY,
    
    /**
     * No period segmentation - all events are in a single period.
     * Used when closing the books pattern is not needed.
     */
    NONE
}

