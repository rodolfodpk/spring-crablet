package com.wallets.features.query;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Controller for wallet query operations.
 * <p>
 * DCB Principle: Single responsibility - handles only read operations.
 * Separate from command controllers to maintain CQRS separation.
 */
@RestController
@RequestMapping("/api/wallets")
@Tag(name = "Wallet Queries", description = "API for querying wallet state and history")
public class WalletQueryController {

    private static final Logger log = LoggerFactory.getLogger(WalletQueryController.class);

    private final WalletQueryService queryService;

    public WalletQueryController(WalletQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Get current wallet state (current balance).
     * GET /api/wallets/{walletId}
     */
    @GetMapping("/{walletId}")
    @Operation(summary = "Get wallet state", description = "Retrieves current wallet balance and metadata")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Wallet found"),
            @ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    public ResponseEntity<WalletResponse> getWallet(
            @Parameter(description = "Wallet ID") @PathVariable String walletId) {
        WalletResponse response = queryService.getWalletState(walletId);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
    }

    /**
     * Get events for a wallet with pagination.
     * GET /api/wallets/{walletId}/events?timestamp={timestamp}&page={page}&size={size}
     */
    @GetMapping("/{walletId}/events")
    @Operation(summary = "Get wallet events", description = "Retrieves paginated events for a wallet including transfers")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    public ResponseEntity<WalletHistoryResponse> getWalletEvents(
            @Parameter(description = "Wallet ID") @PathVariable String walletId,
            @RequestParam(required = false) String timestamp,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Instant targetTime = TimestampFactory.parseTimestamp(timestamp);
        WalletHistoryResponse response = queryService.getWalletHistory(walletId, targetTime, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Get commands for a wallet with their events using CTE queries.
     * GET /api/wallets/{walletId}/commands?timestamp={timestamp}&page={page}&size={size}
     */
    @GetMapping("/{walletId}/commands")
    @Operation(summary = "Get wallet commands", description = "Retrieves paginated commands for a wallet with their events")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Commands retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    public ResponseEntity<WalletCommandsResponse> getWalletCommands(
            @Parameter(description = "Wallet ID") @PathVariable String walletId,
            @RequestParam(required = false) String timestamp,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Instant targetTime = TimestampFactory.parseTimestamp(timestamp);
        WalletCommandsResponse response = queryService.getWalletCommands(walletId, targetTime, page, size);
        return ResponseEntity.ok(response);
    }

}