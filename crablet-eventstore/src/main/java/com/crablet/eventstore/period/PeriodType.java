package com.crablet.eventstore.period;

/**
 * Enum representing different period types for event segmentation.
 * <p>
 * Used in the closing the books pattern to segment events by time periods.
 * Period segmentation is an event organization strategy that allows queries
 * to filter events by time periods, improving performance for large event histories.
 */
public enum PeriodType {
    /**
     * Hourly statements - creates a new statement period each hour.
     */
    HOURLY,
    
    /**
     * Daily statements - creates a new statement period each day.
     */
    DAILY,
    
    /**
     * Monthly statements - creates a new statement period each month.
     */
    MONTHLY,
    
    /**
     * Yearly statements - creates a new statement period each year.
     */
    YEARLY,
    
    /**
     * No period segmentation - all events are in a single period.
     * This is the default when no period configuration is specified.
     * Used when closing the books pattern is not needed.
     */
    NONE
}

