package com.crablet.wallet.api;

import com.crablet.eventpoller.progress.ProcessorStatus;
import com.crablet.outbox.internal.TopicPublisherPair;
import com.crablet.outbox.management.OutboxManagementService;
import com.crablet.outbox.management.OutboxProgressDetails;
import com.crablet.wallet.api.dto.OutboxOperationResponse;
import com.crablet.wallet.api.dto.OutboxProgressDetailsResponse;
import com.crablet.wallet.api.dto.OutboxStatusResponse;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API controller for managing outbox publishers.
 * <p>
 * Provides endpoints for:
 * <ul>
 *   <li>Publisher operations: pause, resume, reset</li>
 *   <li>Status monitoring: get status, lag, all statuses</li>
 *   <li>Detailed progress: get progress information from the outbox_topic_progress table</li>
 * </ul>
 * <p>
 * Each publisher is identified by a (topic, publisher) pair in the URL path:
 * {@code /api/outbox/{topic}/publishers/{publisher}/...}
 */
@RestController
@RequestMapping("/api/outbox")
@Tag(name = "Outbox Management", description = "Transactional outbox publisher management and monitoring")
@ConditionalOnBean(OutboxManagementService.class)
public class OutboxManagementController {

    private final OutboxManagementService outboxManagementService;

    public OutboxManagementController(OutboxManagementService outboxManagementService) {
        this.outboxManagementService = outboxManagementService;
    }

    // ========== Status Endpoints ==========

    @GetMapping("/{topic}/publishers/{publisher}/status")
    @Operation(
            summary = "Get outbox publisher status",
            description = "Retrieves the current status and lag for a specific topic-publisher pair"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Publisher status retrieved successfully",
                    content = @Content(schema = @Schema(implementation = OutboxStatusResponse.class))
            ),
            @ApiResponse(responseCode = "404", description = "Topic/publisher pair not found")
    })
    public ResponseEntity<OutboxStatusResponse> getStatus(
            @Parameter(description = "Topic name", required = true, example = "wallet-events")
            @PathVariable String topic,
            @Parameter(description = "Publisher name", required = true, example = "LogPublisher")
            @PathVariable String publisher) {

        TopicPublisherPair pair = new TopicPublisherPair(topic, publisher);
        Map<TopicPublisherPair, ProcessorStatus> allStatuses = outboxManagementService.getAllStatuses();
        if (!allStatuses.containsKey(pair)) {
            return ResponseEntity.notFound().build();
        }

        ProcessorStatus status = outboxManagementService.getStatus(pair);
        Long lag = outboxManagementService.getLag(pair);

        return ResponseEntity.ok(new OutboxStatusResponse(
                topic, publisher, status.name(), lag != null ? lag : 0L));
    }

    @GetMapping("/status")
    @Operation(
            summary = "Get all outbox publisher statuses",
            description = "Retrieves the status for all configured topic-publisher pairs"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All publisher statuses retrieved successfully")
    })
    public ResponseEntity<Map<String, String>> getAllStatuses() {
        Map<TopicPublisherPair, ProcessorStatus> statuses = outboxManagementService.getAllStatuses();

        // Key format: "{topic}:{publisher}"
        Map<String, String> response = statuses.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().topic() + ":" + e.getKey().publisher(),
                        e -> e.getValue().name()
                ));

        return ResponseEntity.ok(response);
    }

    // ========== Operation Endpoints ==========

    @PostMapping("/{topic}/publishers/{publisher}/pause")
    @Operation(
            summary = "Pause outbox publisher",
            description = "Pauses event publishing for a specific topic-publisher pair"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Publisher paused successfully",
                    content = @Content(schema = @Schema(implementation = OutboxOperationResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Failed to pause publisher")
    })
    public ResponseEntity<OutboxOperationResponse> pause(
            @Parameter(description = "Topic name", required = true, example = "wallet-events")
            @PathVariable String topic,
            @Parameter(description = "Publisher name", required = true, example = "LogPublisher")
            @PathVariable String publisher) {

        TopicPublisherPair pair = new TopicPublisherPair(topic, publisher);
        boolean paused = outboxManagementService.pause(pair);

        if (paused) {
            return ResponseEntity.ok(new OutboxOperationResponse(
                    topic, publisher, "PAUSED", "Outbox publisher paused successfully"));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new OutboxOperationResponse(
                    topic, publisher,
                    outboxManagementService.getStatus(pair).name(),
                    "Failed to pause outbox publisher"));
        }
    }

    @PostMapping("/{topic}/publishers/{publisher}/resume")
    @Operation(
            summary = "Resume outbox publisher",
            description = "Resumes event publishing for a paused topic-publisher pair"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Publisher resumed successfully",
                    content = @Content(schema = @Schema(implementation = OutboxOperationResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Failed to resume publisher")
    })
    public ResponseEntity<OutboxOperationResponse> resume(
            @Parameter(description = "Topic name", required = true, example = "wallet-events")
            @PathVariable String topic,
            @Parameter(description = "Publisher name", required = true, example = "LogPublisher")
            @PathVariable String publisher) {

        TopicPublisherPair pair = new TopicPublisherPair(topic, publisher);
        boolean resumed = outboxManagementService.resume(pair);

        if (resumed) {
            return ResponseEntity.ok(new OutboxOperationResponse(
                    topic, publisher, "ACTIVE", "Outbox publisher resumed successfully"));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new OutboxOperationResponse(
                    topic, publisher,
                    outboxManagementService.getStatus(pair).name(),
                    "Failed to resume outbox publisher"));
        }
    }

    @PostMapping("/{topic}/publishers/{publisher}/reset")
    @Operation(
            summary = "Reset outbox publisher",
            description = "Resets a failed outbox publisher back to active state"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Publisher reset successfully",
                    content = @Content(schema = @Schema(implementation = OutboxOperationResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Failed to reset publisher")
    })
    public ResponseEntity<OutboxOperationResponse> reset(
            @Parameter(description = "Topic name", required = true, example = "wallet-events")
            @PathVariable String topic,
            @Parameter(description = "Publisher name", required = true, example = "LogPublisher")
            @PathVariable String publisher) {

        TopicPublisherPair pair = new TopicPublisherPair(topic, publisher);
        boolean reset = outboxManagementService.reset(pair);

        if (reset) {
            return ResponseEntity.ok(new OutboxOperationResponse(
                    topic, publisher, "ACTIVE", "Outbox publisher reset successfully"));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new OutboxOperationResponse(
                    topic, publisher,
                    outboxManagementService.getStatus(pair).name(),
                    "Failed to reset outbox publisher"));
        }
    }

    // ========== Detailed Progress Endpoints ==========

    @GetMapping("/{topic}/publishers/{publisher}/details")
    @Operation(
            summary = "Get detailed outbox publisher progress",
            description = "Retrieves comprehensive progress information for a specific topic-publisher pair"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Publisher progress details retrieved successfully",
                    content = @Content(schema = @Schema(implementation = OutboxProgressDetailsResponse.class))
            ),
            @ApiResponse(responseCode = "404", description = "Topic/publisher pair not found")
    })
    public ResponseEntity<OutboxProgressDetailsResponse> getProgressDetails(
            @Parameter(description = "Topic name", required = true, example = "wallet-events")
            @PathVariable String topic,
            @Parameter(description = "Publisher name", required = true, example = "LogPublisher")
            @PathVariable String publisher) {

        OutboxProgressDetails details = outboxManagementService.getProgressDetails(topic, publisher);

        if (details == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(OutboxProgressDetailsResponse.from(details));
    }

    @GetMapping("/details")
    @Operation(
            summary = "Get all outbox publisher progress details",
            description = "Retrieves comprehensive progress information for all topic-publisher pairs"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All publisher progress details retrieved successfully")
    })
    public ResponseEntity<List<OutboxProgressDetailsResponse>> getAllProgressDetails() {
        List<OutboxProgressDetails> allDetails = outboxManagementService.getAllProgressDetails();

        List<OutboxProgressDetailsResponse> response = allDetails.stream()
                .map(OutboxProgressDetailsResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // ========== Lag Endpoint ==========

    @GetMapping("/{topic}/publishers/{publisher}/lag")
    @Operation(
            summary = "Get outbox publisher lag",
            description = "Retrieves the number of events this publisher is behind"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Publisher lag retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Topic/publisher pair not found")
    })
    public ResponseEntity<Map<String, Object>> getLag(
            @Parameter(description = "Topic name", required = true, example = "wallet-events")
            @PathVariable String topic,
            @Parameter(description = "Publisher name", required = true, example = "LogPublisher")
            @PathVariable String publisher) {

        TopicPublisherPair pair = new TopicPublisherPair(topic, publisher);
        Map<TopicPublisherPair, ProcessorStatus> allStatuses = outboxManagementService.getAllStatuses();
        if (!allStatuses.containsKey(pair)) {
            return ResponseEntity.notFound().build();
        }

        Long lag = outboxManagementService.getLag(pair);

        return ResponseEntity.ok(Map.of(
                "topic", topic,
                "publisher", publisher,
                "lag", lag != null ? lag : 0L
        ));
    }
}
