package com.crablet.wallet.api;

import com.crablet.automations.management.AutomationManagementService;
import com.crablet.automations.management.AutomationProgressDetails;
import com.crablet.eventpoller.progress.ProcessorStatus;
import com.crablet.wallet.api.dto.AutomationOperationResponse;
import com.crablet.wallet.api.dto.AutomationProgressDetailsResponse;
import com.crablet.wallet.api.dto.AutomationStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
 * REST API controller for managing event-driven automations.
 * <p>
 * Provides endpoints for:
 * <ul>
 *   <li>Automation operations: pause, resume, reset</li>
 *   <li>Status monitoring: get status, lag, all statuses</li>
 *   <li>Detailed progress: get progress information from the reaction_progress table</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/automations")
@Tag(name = "Automation Management", description = "Event-driven automation management and monitoring")
@ConditionalOnBean(AutomationManagementService.class)
public class AutomationsManagementController {

    private final AutomationManagementService automationManagementService;

    public AutomationsManagementController(AutomationManagementService automationManagementService) {
        this.automationManagementService = automationManagementService;
    }

    // ========== Status Endpoints ==========

    @GetMapping("/{automationName}/status")
    @Operation(
            summary = "Get automation status",
            description = "Retrieves the current status and lag for an automation"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Automation status retrieved successfully",
                    content = @Content(schema = @Schema(implementation = AutomationStatusResponse.class))
            ),
            @ApiResponse(responseCode = "404", description = "Automation not found")
    })
    public ResponseEntity<AutomationStatusResponse> getStatus(
            @Parameter(description = "Automation name", required = true, example = "wallet-notification")
            @PathVariable String automationName) {

        Map<String, ProcessorStatus> allStatuses = automationManagementService.getAllStatuses();
        if (!allStatuses.containsKey(automationName)) {
            return ResponseEntity.notFound().build();
        }

        ProcessorStatus status = automationManagementService.getStatus(automationName);
        Long lag = automationManagementService.getLag(automationName);

        return ResponseEntity.ok(new AutomationStatusResponse(
                automationName,
                status.name(),
                lag != null ? lag : 0L
        ));
    }

    @GetMapping("/status")
    @Operation(
            summary = "Get all automation statuses",
            description = "Retrieves the status for all registered automations"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All automation statuses retrieved successfully")
    })
    public ResponseEntity<Map<String, String>> getAllStatuses() {
        Map<String, ProcessorStatus> statuses = automationManagementService.getAllStatuses();

        Map<String, String> response = statuses.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().name()));

        return ResponseEntity.ok(response);
    }

    // ========== Operation Endpoints ==========

    @PostMapping("/{automationName}/pause")
    @Operation(
            summary = "Pause automation",
            description = "Pauses event processing for an automation"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Automation paused successfully",
                    content = @Content(schema = @Schema(implementation = AutomationOperationResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Failed to pause automation")
    })
    public ResponseEntity<AutomationOperationResponse> pause(
            @Parameter(description = "Automation name", required = true, example = "wallet-notification")
            @PathVariable String automationName) {

        boolean paused = automationManagementService.pause(automationName);

        if (paused) {
            return ResponseEntity.ok(new AutomationOperationResponse(
                    automationName, "PAUSED", "Automation paused successfully"));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AutomationOperationResponse(
                    automationName,
                    automationManagementService.getStatus(automationName).name(),
                    "Failed to pause automation"));
        }
    }

    @PostMapping("/{automationName}/resume")
    @Operation(
            summary = "Resume automation",
            description = "Resumes event processing for a paused automation"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Automation resumed successfully",
                    content = @Content(schema = @Schema(implementation = AutomationOperationResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Failed to resume automation")
    })
    public ResponseEntity<AutomationOperationResponse> resume(
            @Parameter(description = "Automation name", required = true, example = "wallet-notification")
            @PathVariable String automationName) {

        boolean resumed = automationManagementService.resume(automationName);

        if (resumed) {
            return ResponseEntity.ok(new AutomationOperationResponse(
                    automationName, "ACTIVE", "Automation resumed successfully"));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AutomationOperationResponse(
                    automationName,
                    automationManagementService.getStatus(automationName).name(),
                    "Failed to resume automation"));
        }
    }

    @PostMapping("/{automationName}/reset")
    @Operation(
            summary = "Reset automation",
            description = "Resets a failed automation back to active state"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Automation reset successfully",
                    content = @Content(schema = @Schema(implementation = AutomationOperationResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Failed to reset automation")
    })
    public ResponseEntity<AutomationOperationResponse> reset(
            @Parameter(description = "Automation name", required = true, example = "wallet-notification")
            @PathVariable String automationName) {

        boolean reset = automationManagementService.reset(automationName);

        if (reset) {
            return ResponseEntity.ok(new AutomationOperationResponse(
                    automationName, "ACTIVE", "Automation reset successfully"));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AutomationOperationResponse(
                    automationName,
                    automationManagementService.getStatus(automationName).name(),
                    "Failed to reset automation"));
        }
    }

    // ========== Detailed Progress Endpoints ==========

    @GetMapping("/{automationName}/details")
    @Operation(
            summary = "Get detailed automation progress",
            description = "Retrieves comprehensive progress information for an automation, including error details and timestamps"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Automation progress details retrieved successfully",
                    content = @Content(schema = @Schema(implementation = AutomationProgressDetailsResponse.class))
            ),
            @ApiResponse(responseCode = "404", description = "Automation not found")
    })
    public ResponseEntity<AutomationProgressDetailsResponse> getProgressDetails(
            @Parameter(description = "Automation name", required = true, example = "wallet-notification")
            @PathVariable String automationName) {

        AutomationProgressDetails details = automationManagementService.getProgressDetails(automationName);

        if (details == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(AutomationProgressDetailsResponse.from(details));
    }

    @GetMapping("/details")
    @Operation(
            summary = "Get all automation progress details",
            description = "Retrieves comprehensive progress information for all automations"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All automation progress details retrieved successfully")
    })
    public ResponseEntity<Map<String, AutomationProgressDetailsResponse>> getAllProgressDetails() {
        Map<String, AutomationProgressDetails> allDetails = automationManagementService.getAllProgressDetails();

        Map<String, AutomationProgressDetailsResponse> response = allDetails.values().stream()
                .collect(Collectors.toMap(
                        AutomationProgressDetails::automationName,
                        AutomationProgressDetailsResponse::from
                ));

        return ResponseEntity.ok(response);
    }

    // ========== Lag Endpoint ==========

    @GetMapping("/{automationName}/lag")
    @Operation(
            summary = "Get automation lag",
            description = "Retrieves the number of events this automation is behind"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Automation lag retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Automation not found")
    })
    public ResponseEntity<Map<String, Object>> getLag(
            @Parameter(description = "Automation name", required = true, example = "wallet-notification")
            @PathVariable String automationName) {

        Map<String, ProcessorStatus> allStatuses = automationManagementService.getAllStatuses();
        if (!allStatuses.containsKey(automationName)) {
            return ResponseEntity.notFound().build();
        }

        Long lag = automationManagementService.getLag(automationName);

        return ResponseEntity.ok(Map.of(
                "automationName", automationName,
                "lag", lag != null ? lag : 0L
        ));
    }
}
