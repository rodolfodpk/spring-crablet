package com.crablet.eventprocessor.management;

import com.crablet.eventprocessor.processor.EventProcessor;
import com.crablet.eventprocessor.progress.ProcessorStatus;
import com.crablet.eventprocessor.progress.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic implementation of ProcessorManagementService.
 * 
 * @param <T> Processor configuration type
 * @param <I> Processor identifier type
 */
public class ProcessorManagementServiceImpl<T extends com.crablet.eventprocessor.processor.ProcessorConfig<I>, I> 
        implements ProcessorManagementService<I> {
    
    private static final Logger log = LoggerFactory.getLogger(ProcessorManagementServiceImpl.class);
    
    private final EventProcessor<T, I> eventProcessor;
    private final ProgressTracker<I> progressTracker;
    private final DataSource readDataSource;
    
    private static final String SELECT_MAX_POSITION_SQL = 
        "SELECT COALESCE(MAX(position), 0) FROM events";
    
    public ProcessorManagementServiceImpl(
            EventProcessor<T, I> eventProcessor,
            ProgressTracker<I> progressTracker,
            DataSource readDataSource) {
        if (eventProcessor == null) {
            throw new IllegalArgumentException("eventProcessor must not be null");
        }
        if (progressTracker == null) {
            throw new IllegalArgumentException("progressTracker must not be null");
        }
        if (readDataSource == null) {
            throw new IllegalArgumentException("readDataSource must not be null");
        }
        this.eventProcessor = eventProcessor;
        this.progressTracker = progressTracker;
        this.readDataSource = readDataSource;
    }
    
    @Override
    public boolean pause(I processorId) {
        // Check if processor exists by checking getAllStatuses
        // (getStatus returns ACTIVE for non-existent processors, so we can't rely on null check)
        Map<I, ProcessorStatus> allStatuses = eventProcessor.getAllStatuses();
        if (!allStatuses.containsKey(processorId)) {
            log.warn("Processor {} not found for pause operation", processorId);
            return false;
        }
        
        eventProcessor.pause(processorId);
        log.info("Processor {} paused successfully", processorId);
        return true;
    }
    
    @Override
    public boolean resume(I processorId) {
        // Check if processor exists
        Map<I, ProcessorStatus> allStatuses = eventProcessor.getAllStatuses();
        if (!allStatuses.containsKey(processorId)) {
            log.warn("Processor {} not found for resume operation", processorId);
            return false;
        }
        
        eventProcessor.resume(processorId);
        log.info("Processor {} resumed successfully", processorId);
        return true;
    }
    
    @Override
    public boolean reset(I processorId) {
        // Check if processor exists
        Map<I, ProcessorStatus> allStatuses = eventProcessor.getAllStatuses();
        if (!allStatuses.containsKey(processorId)) {
            log.warn("Processor {} not found for reset operation", processorId);
            return false;
        }
        
        // Reset: clear errors via ProgressTracker and resume
        progressTracker.resetErrorCount(processorId);
        progressTracker.setStatus(processorId, ProcessorStatus.ACTIVE);
        eventProcessor.resume(processorId);
        log.info("Processor {} reset successfully", processorId);
        return true;
    }
    
    @Override
    public ProcessorStatus getStatus(I processorId) {
        return eventProcessor.getStatus(processorId);
    }
    
    @Override
    public Map<I, ProcessorStatus> getAllStatuses() {
        return eventProcessor.getAllStatuses();
    }
    
    @Override
    public Long getLag(I processorId) {
        // Get max position from events table
        try (Connection connection = readDataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(SELECT_MAX_POSITION_SQL);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                Long maxPosition = rs.getLong(1);
                if (rs.wasNull()) {
                    return null;
                }
                
                // Get last processed position from progress tracker
                long lastPosition = progressTracker.getLastPosition(processorId);
                
                return maxPosition - lastPosition;
            }
            
            return null;
        } catch (SQLException e) {
            log.error("Failed to calculate lag for processor: {}", processorId, e);
            throw new RuntimeException("Failed to calculate lag for processor: " + processorId, e);
        }
    }
    
    @Override
    public BackoffInfo getBackoffInfo(I processorId) {
        // Get backoff state from EventProcessor
        if (eventProcessor instanceof com.crablet.eventprocessor.processor.EventProcessorImpl) {
            @SuppressWarnings("unchecked")
            com.crablet.eventprocessor.processor.EventProcessorImpl<T, I> impl = 
                (com.crablet.eventprocessor.processor.EventProcessorImpl<T, I>) eventProcessor;
            
            com.crablet.eventprocessor.backoff.BackoffState backoffState = impl.getBackoffState(processorId);
            if (backoffState == null) {
                return null;
            }
            
            return new BackoffInfo(
                backoffState.getEmptyPollCount(),
                backoffState.getCurrentSkipCounter()
            );
        }
        
        return null;
    }
    
    @Override
    public Map<I, BackoffInfo> getAllBackoffInfo() {
        Map<I, BackoffInfo> result = new HashMap<>();
        
        if (eventProcessor instanceof com.crablet.eventprocessor.processor.EventProcessorImpl) {
            @SuppressWarnings("unchecked")
            com.crablet.eventprocessor.processor.EventProcessorImpl<T, I> impl = 
                (com.crablet.eventprocessor.processor.EventProcessorImpl<T, I>) eventProcessor;
            
            Map<I, com.crablet.eventprocessor.backoff.BackoffState> backoffStates = impl.getAllBackoffStates();
            for (var entry : backoffStates.entrySet()) {
                com.crablet.eventprocessor.backoff.BackoffState state = entry.getValue();
                result.put(entry.getKey(), new BackoffInfo(
                    state.getEmptyPollCount(),
                    state.getCurrentSkipCounter()
                ));
            }
        }
        
        return result;
    }
}

