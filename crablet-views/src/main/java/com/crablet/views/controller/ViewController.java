package com.crablet.views.controller;

import com.crablet.eventprocessor.management.ProcessorManagementService;
import com.crablet.eventprocessor.progress.ProcessorStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Generic REST controller for managing view projections.
 * Provides endpoints for status, pause, resume, and reset operations.
 */
@RestController
@RequestMapping("/api/views")
public class ViewController {
    
    private final ProcessorManagementService<String> managementService;
    
    public ViewController(ProcessorManagementService<String> managementService) {
        this.managementService = managementService;
    }
    
    /**
     * Get status of a view projection.
     * Example: GET /api/views/wallet-view/status
     */
    @GetMapping("/{viewName}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String viewName) {
        ProcessorStatus status = managementService.getStatus(viewName);
        long lag = managementService.getLag(viewName);
        
        Map<String, Object> response = Map.of(
            "viewName", viewName,
            "status", status.name(),
            "lag", lag
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Pause a view projection.
     * Example: POST /api/views/wallet-view/pause
     */
    @PostMapping("/{viewName}/pause")
    public ResponseEntity<Map<String, Object>> pause(@PathVariable String viewName) {
        boolean paused = managementService.pause(viewName);
        
        if (paused) {
            return ResponseEntity.ok(Map.of(
                "viewName", viewName,
                "status", "PAUSED",
                "message", "View projection paused successfully"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "viewName", viewName,
                "error", "Failed to pause view projection"
            ));
        }
    }
    
    /**
     * Resume a paused view projection.
     * Example: POST /api/views/wallet-view/resume
     */
    @PostMapping("/{viewName}/resume")
    public ResponseEntity<Map<String, Object>> resume(@PathVariable String viewName) {
        boolean resumed = managementService.resume(viewName);
        
        if (resumed) {
            return ResponseEntity.ok(Map.of(
                "viewName", viewName,
                "status", "ACTIVE",
                "message", "View projection resumed successfully"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "viewName", viewName,
                "error", "Failed to resume view projection"
            ));
        }
    }
    
    /**
     * Reset a failed view projection.
     * Example: POST /api/views/wallet-view/reset
     */
    @PostMapping("/{viewName}/reset")
    public ResponseEntity<Map<String, Object>> reset(@PathVariable String viewName) {
        boolean reset = managementService.reset(viewName);
        
        if (reset) {
            return ResponseEntity.ok(Map.of(
                "viewName", viewName,
                "status", "ACTIVE",
                "message", "View projection reset successfully"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "viewName", viewName,
                "error", "Failed to reset view projection"
            ));
        }
    }
    
    /**
     * Get lag for a view projection.
     * Example: GET /api/views/wallet-view/lag
     */
    @GetMapping("/{viewName}/lag")
    public ResponseEntity<Map<String, Object>> getLag(@PathVariable String viewName) {
        long lag = managementService.getLag(viewName);
        
        return ResponseEntity.ok(Map.of(
            "viewName", viewName,
            "lag", lag
        ));
    }
}

