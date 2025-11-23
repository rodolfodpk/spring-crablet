package com.crablet.eventprocessor.management;

import com.crablet.eventprocessor.progress.ProcessorStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Generic REST controller for managing processors.
 * 
 * <p><strong>Note:</strong> This controller works with String-based processor IDs.
 * For other processor ID types, implementations should create their own controllers
 * or provide a String-to-ID converter.
 * 
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/processors - Get all processor statuses</li>
 *   <li>GET /api/processors/{id} - Get processor status</li>
 *   <li>POST /api/processors/{id}/pause - Pause processor</li>
 *   <li>POST /api/processors/{id}/resume - Resume processor</li>
 *   <li>POST /api/processors/{id}/reset - Reset failed processor</li>
 *   <li>GET /api/processors/{id}/lag - Get processor lag</li>
 *   <li>GET /api/processors/{id}/backoff - Get processor backoff info</li>
 *   <li>GET /api/processors/backoff - Get all processor backoff info</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/processors")
@ConditionalOnBean(name = "processorManagementService", value = ProcessorManagementService.class)
public class ProcessorManagementController {
    
    private final ProcessorManagementService<String> managementService;
    
    public ProcessorManagementController(ProcessorManagementService<String> managementService) {
        this.managementService = managementService;
    }
    
    /**
     * Get status of all processors.
     */
    @GetMapping
    public ResponseEntity<Map<String, ProcessorStatus>> getAllStatuses() {
        Map<String, ProcessorStatus> statuses = managementService.getAllStatuses();
        return ResponseEntity.ok(statuses);
    }
    
    /**
     * Get status of a specific processor.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProcessorStatus> getStatus(@PathVariable String id) {
        ProcessorStatus status = managementService.getStatus(id);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
    
    /**
     * Pause a processor.
     */
    @PostMapping("/{id}/pause")
    public ResponseEntity<Void> pause(@PathVariable String id) {
        boolean success = managementService.pause(id);
        if (success) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Resume a processor.
     */
    @PostMapping("/{id}/resume")
    public ResponseEntity<Void> resume(@PathVariable String id) {
        boolean success = managementService.resume(id);
        if (success) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Reset a failed processor.
     */
    @PostMapping("/{id}/reset")
    public ResponseEntity<Void> reset(@PathVariable String id) {
        boolean success = managementService.reset(id);
        if (success) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Get processor lag information.
     */
    @GetMapping("/{id}/lag")
    public ResponseEntity<Long> getLag(@PathVariable String id) {
        Long lag = managementService.getLag(id);
        if (lag == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(lag);
    }
    
    /**
     * Get backoff information for a specific processor.
     */
    @GetMapping("/{id}/backoff")
    public ResponseEntity<ProcessorManagementService.BackoffInfo> getBackoffInfo(@PathVariable String id) {
        ProcessorManagementService.BackoffInfo info = managementService.getBackoffInfo(id);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(info);
    }
    
    /**
     * Get backoff information for all processors.
     */
    @GetMapping("/backoff")
    public ResponseEntity<Map<String, ProcessorManagementService.BackoffInfo>> getAllBackoffInfo() {
        Map<String, ProcessorManagementService.BackoffInfo> backoffInfo = managementService.getAllBackoffInfo();
        return ResponseEntity.ok(backoffInfo);
    }
}

