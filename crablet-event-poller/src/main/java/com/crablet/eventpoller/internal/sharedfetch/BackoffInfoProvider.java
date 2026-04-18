package com.crablet.eventpoller.internal.sharedfetch;

import com.crablet.eventpoller.internal.BackoffState;

import java.util.Map;

/**
 * Capability interface for exposing per-processor backoff state.
 * Implemented by both {@code EventProcessorImpl} (per-processor backoff) and
 * {@code SharedFetchModuleProcessor} (module-level backoff).
 * <p>
 * {@code ProcessorManagementServiceImpl} targets this interface instead of
 * casting to the concrete {@code EventProcessorImpl} class.
 */
public interface BackoffInfoProvider<I> {

    BackoffState getBackoffStateForProcessor(I processorId);

    Map<I, BackoffState> getAllBackoffStates();
}
