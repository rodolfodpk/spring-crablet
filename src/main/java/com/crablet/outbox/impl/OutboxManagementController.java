package com.crablet.outbox.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing outbox publisher operations.
 * Provides HTTP endpoints for pausing, resuming, and monitoring publishers.
 */
@RestController
@RequestMapping("/api/outbox")
public class OutboxManagementController {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxManagementController.class);
    
    private final OutboxManagementService outboxManagementService;
    
    public OutboxManagementController(OutboxManagementService outboxManagementService) {
        this.outboxManagementService = outboxManagementService;
    }
    
    /**
     * Get status of all publishers.
     */
    @GetMapping("/publishers")
    public ResponseEntity<List<OutboxManagementService.PublisherStatus>> getAllPublisherStatus() {
        List<OutboxManagementService.PublisherStatus> statuses = outboxManagementService.getAllPublisherStatus();
        return ResponseEntity.ok(statuses);
    }
    
    /**
     * Get status of a specific publisher.
     */
    @GetMapping("/publishers/{publisherName}")
    public ResponseEntity<OutboxManagementService.PublisherStatus> getPublisherStatus(
            @PathVariable String publisherName) {
        
        OutboxManagementService.PublisherStatus status = outboxManagementService.getPublisherStatus(publisherName);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
    
    /**
     * Pause a publisher.
     */
    @PostMapping("/publishers/{publisherName}/pause")
    public ResponseEntity<Void> pausePublisher(@PathVariable String publisherName) {
        boolean success = outboxManagementService.pausePublisher(publisherName);
        if (success) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Resume a publisher.
     */
    @PostMapping("/publishers/{publisherName}/resume")
    public ResponseEntity<Void> resumePublisher(@PathVariable String publisherName) {
        boolean success = outboxManagementService.resumePublisher(publisherName);
        if (success) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Reset a failed publisher.
     */
    @PostMapping("/publishers/{publisherName}/reset")
    public ResponseEntity<Void> resetPublisher(@PathVariable String publisherName) {
        boolean success = outboxManagementService.resetPublisher(publisherName);
        if (success) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Get publisher lag information.
     */
    @GetMapping("/publishers/lag")
    public ResponseEntity<Map<String, Long>> getPublisherLag() {
        Map<String, Long> lag = outboxManagementService.getPublisherLag();
        return ResponseEntity.ok(lag);
    }
    
    /**
     * Get current leader instances for all publishers.
     */
    @GetMapping("/publishers/leaders")
    public ResponseEntity<Map<String, String>> getCurrentLeaders() {
        Map<String, String> leaders = outboxManagementService.getCurrentLeaders();
        return ResponseEntity.ok(leaders);
    }
}
