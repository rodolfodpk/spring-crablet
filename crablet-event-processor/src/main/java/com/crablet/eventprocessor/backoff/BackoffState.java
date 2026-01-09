package com.crablet.eventprocessor.backoff;

/**
 * Tracks exponential backoff state for a processor instance.
 * <p>
 * After N consecutive empty polls, the processor starts skipping poll cycles
 * exponentially to reduce unnecessary database queries during idle periods.
 * Backoff resets immediately when events are found.
 */
public class BackoffState {
    private final int threshold;
    private final int multiplier;
    private final long maxSkips;
    
    private int emptyPollCount = 0;
    private int skipCounter = 0;
    
    public BackoffState(int threshold, int multiplier, long pollingIntervalMs, int maxBackoffSeconds) {
        this.threshold = threshold;
        this.multiplier = multiplier;
        this.maxSkips = (maxBackoffSeconds * 1000L) / pollingIntervalMs;
    }
    
    /**
     * Check if the current poll cycle should be skipped.
     */
    public boolean shouldSkip() {
        if (skipCounter > 0) {
            skipCounter--;
            return true;
        }
        return false;
    }
    
    /**
     * Record an empty poll (no events found).
     */
    public void recordEmpty() {
        emptyPollCount++;
        
        if (emptyPollCount > threshold) {
            int exponent = emptyPollCount - threshold;
            long skipsToAdd = (long) Math.pow(multiplier, exponent) - 1;
            skipCounter = (int) Math.min(skipsToAdd, maxSkips);
        }
    }
    
    /**
     * Record a successful processing (events found and processed).
     * Resets backoff immediately.
     */
    public void recordSuccess() {
        emptyPollCount = 0;
        skipCounter = 0;
    }
    
    /**
     * Get the number of consecutive empty polls.
     */
    public int getEmptyPollCount() {
        return emptyPollCount;
    }
    
    /**
     * Get the current number of skips remaining.
     */
    public int getCurrentSkipCounter() {
        return skipCounter;
    }
}

