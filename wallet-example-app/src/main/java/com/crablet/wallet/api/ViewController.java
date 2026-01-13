package com.crablet.wallet.api;

import com.crablet.eventprocessor.progress.ProcessorStatus;
import com.crablet.views.service.ViewManagementService;
import com.crablet.views.service.ViewProgressDetails;
import com.crablet.wallet.api.dto.ViewOperationResponse;
import com.crablet.wallet.api.dto.ViewProgressDetailsResponse;
import com.crablet.wallet.api.dto.ViewStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API controller for managing view projections.
 * <p>
 * Provides endpoints for:
 * <ul>
 *   <li>View operations: pause, resume, reset</li>
 *   <li>Status monitoring: get status, lag, all statuses</li>
 *   <li>Detailed progress: get detailed progress information</li>
 * </ul>
 * <p>
 * Uses {@link ViewManagementService} for unified view management.
 */
@RestController
@RequestMapping("/api/views")
@Tag(name = "View Management", description = "View projection management and monitoring")
public class ViewController {

    private final ViewManagementService viewManagementService;

    public ViewController(ViewManagementService viewManagementService) {
        this.viewManagementService = viewManagementService;
    }

    // ========== Status Endpoints ==========

    @GetMapping("/{viewName}/status")
    @Operation(
            summary = "Get view status",
            description = "Retrieves the current status and lag for a view projection"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "View status retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ViewStatusResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "View not found"
            )
    })
    public ResponseEntity<ViewStatusResponse> getStatus(
            @Parameter(description = "View name", required = true, example = "wallet-balance-view")
            @PathVariable String viewName) {
        
        // Check if view exists by checking if it's in the status map
        Map<String, ProcessorStatus> allStatuses = viewManagementService.getAllStatuses();
        if (!allStatuses.containsKey(viewName)) {
            return ResponseEntity.notFound().build();
        }
        
        ProcessorStatus status = viewManagementService.getStatus(viewName);
        Long lag = viewManagementService.getLag(viewName);
        
        ViewStatusResponse response = new ViewStatusResponse(
                viewName,
                status.name(),
                lag != null ? lag : 0L
        );
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    @Operation(
            summary = "Get all view statuses",
            description = "Retrieves the status for all configured view projections"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "All view statuses retrieved successfully"
            )
    })
    public ResponseEntity<Map<String, String>> getAllStatuses() {
        Map<String, ProcessorStatus> statuses = viewManagementService.getAllStatuses();
        
        Map<String, String> response = statuses.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().name()
                ));
        
        return ResponseEntity.ok(response);
    }

    // ========== Operation Endpoints ==========

    @PostMapping("/{viewName}/pause")
    @Operation(
            summary = "Pause view processing",
            description = "Pauses the processing of a view projection"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "View paused successfully",
                    content = @Content(schema = @Schema(implementation = ViewOperationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Failed to pause view (view not found or already paused)"
            )
    })
    public ResponseEntity<ViewOperationResponse> pause(
            @Parameter(description = "View name", required = true, example = "wallet-balance-view")
            @PathVariable String viewName) {
        
        boolean paused = viewManagementService.pause(viewName);
        
        if (paused) {
            ViewOperationResponse response = new ViewOperationResponse(
                    viewName,
                    "PAUSED",
                    "View projection paused successfully"
            );
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new ViewOperationResponse(
                            viewName,
                            viewManagementService.getStatus(viewName).name(),
                            "Failed to pause view projection"
                    )
            );
        }
    }

    @PostMapping("/{viewName}/resume")
    @Operation(
            summary = "Resume view processing",
            description = "Resumes the processing of a paused view projection"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "View resumed successfully",
                    content = @Content(schema = @Schema(implementation = ViewOperationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Failed to resume view (view not found or not paused)"
            )
    })
    public ResponseEntity<ViewOperationResponse> resume(
            @Parameter(description = "View name", required = true, example = "wallet-balance-view")
            @PathVariable String viewName) {
        
        boolean resumed = viewManagementService.resume(viewName);
        
        if (resumed) {
            ViewOperationResponse response = new ViewOperationResponse(
                    viewName,
                    "ACTIVE",
                    "View projection resumed successfully"
            );
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new ViewOperationResponse(
                            viewName,
                            viewManagementService.getStatus(viewName).name(),
                            "Failed to resume view projection"
                    )
            );
        }
    }

    @PostMapping("/{viewName}/reset")
    @Operation(
            summary = "Reset view processing",
            description = "Resets a failed view projection to active state"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "View reset successfully",
                    content = @Content(schema = @Schema(implementation = ViewOperationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Failed to reset view (view not found)"
            )
    })
    public ResponseEntity<ViewOperationResponse> reset(
            @Parameter(description = "View name", required = true, example = "wallet-balance-view")
            @PathVariable String viewName) {
        
        boolean reset = viewManagementService.reset(viewName);
        
        if (reset) {
            ViewOperationResponse response = new ViewOperationResponse(
                    viewName,
                    "ACTIVE",
                    "View projection reset successfully"
            );
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new ViewOperationResponse(
                            viewName,
                            viewManagementService.getStatus(viewName).name(),
                            "Failed to reset view projection"
                    )
            );
        }
    }

    // ========== Detailed Progress Endpoints ==========

    @GetMapping("/{viewName}/details")
    @Operation(
            summary = "Get detailed view progress",
            description = "Retrieves comprehensive progress information for a view projection, including error details and timestamps"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "View progress details retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ViewProgressDetailsResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "View not found"
            )
    })
    public ResponseEntity<ViewProgressDetailsResponse> getProgressDetails(
            @Parameter(description = "View name", required = true, example = "wallet-balance-view")
            @PathVariable String viewName) {
        
        ViewProgressDetails details = viewManagementService.getProgressDetails(viewName);
        
        if (details == null) {
            return ResponseEntity.notFound().build();
        }
        
        ViewProgressDetailsResponse response = ViewProgressDetailsResponse.from(details);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/details")
    @Operation(
            summary = "Get all view progress details",
            description = "Retrieves comprehensive progress information for all view projections"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "All view progress details retrieved successfully"
            )
    })
    public ResponseEntity<Map<String, ViewProgressDetailsResponse>> getAllProgressDetails() {
        Map<String, ViewProgressDetails> allDetails = viewManagementService.getAllProgressDetails();
        
        Map<String, ViewProgressDetailsResponse> response = allDetails.values().stream()
                .collect(Collectors.toMap(
                        ViewProgressDetails::viewName,
                        ViewProgressDetailsResponse::from
                ));
        
        return ResponseEntity.ok(response);
    }

    // ========== Lag Endpoint ==========

    @GetMapping("/{viewName}/lag")
    @Operation(
            summary = "Get view lag",
            description = "Retrieves the number of events behind for a view projection"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "View lag retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "View not found"
            )
    })
    public ResponseEntity<Map<String, Object>> getLag(
            @Parameter(description = "View name", required = true, example = "wallet-balance-view")
            @PathVariable String viewName) {
        
        // Check if view exists by checking if it's in the status map
        Map<String, ProcessorStatus> allStatuses = viewManagementService.getAllStatuses();
        if (!allStatuses.containsKey(viewName)) {
            return ResponseEntity.notFound().build();
        }
        
        Long lag = viewManagementService.getLag(viewName);
        
        return ResponseEntity.ok(Map.of(
                "viewName", viewName,
                "lag", lag != null ? lag : 0L
        ));
    }
}
