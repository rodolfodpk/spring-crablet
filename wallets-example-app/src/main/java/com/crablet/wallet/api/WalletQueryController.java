package com.crablet.wallet.api;

import com.crablet.wallet.api.dto.WalletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Read-only query controller for wallet views.
 * Queries materialized views (not event store) for fast reads.
 */
@RestController
@RequestMapping("/api/wallets")
@Tag(name = "Wallet Queries", description = "Read-only queries from materialized views")
public class WalletQueryController {

    private final JdbcTemplate jdbcTemplate;

    public WalletQueryController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/{walletId}")
    @Operation(
            summary = "Get wallet balance",
            description = "Retrieves wallet balance and owner information from materialized view"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Wallet found",
                    content = @Content(schema = @Schema(implementation = WalletResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Wallet not found"
            )
    })
    public ResponseEntity<WalletResponse> getWallet(
            @Parameter(description = "Wallet identifier", required = true)
            @PathVariable String walletId) {
        
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT wallet_id, owner, balance, last_updated_at FROM wallet_balance_view WHERE wallet_id = ?",
                walletId
        );
        
        if (results.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> row = results.get(0);
        WalletResponse response = new WalletResponse(
                (String) row.get("wallet_id"),
                (String) row.get("owner"),
                ((BigDecimal) row.get("balance")).intValue(),
                ((java.sql.Timestamp) row.get("last_updated_at")).toInstant()
        );
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{walletId}/transactions")
    @Operation(
            summary = "Get wallet transaction history",
            description = "Retrieves transaction history for a wallet with pagination"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transaction history retrieved"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Wallet not found"
            )
    })
    public ResponseEntity<Map<String, Object>> getTransactions(
            @Parameter(description = "Wallet identifier", required = true)
            @PathVariable String walletId,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        
        // Check if wallet exists
        Integer walletExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallet_balance_view WHERE wallet_id = ?",
                Integer.class,
                walletId
        );
        
        if (walletExists == null || walletExists == 0) {
            return ResponseEntity.notFound().build();
        }
        
        // Get total count
        Integer totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallet_transaction_view WHERE wallet_id = ?",
                Integer.class,
                walletId
        );
        
        // Get paginated transactions
        List<Map<String, Object>> transactions = jdbcTemplate.queryForList(
                """
                SELECT transaction_id, event_type, amount, description, occurred_at
                FROM wallet_transaction_view
                WHERE wallet_id = ?
                ORDER BY occurred_at DESC
                LIMIT ? OFFSET ?
                """,
                walletId,
                size,
                page * size
        );
        
        return ResponseEntity.ok(Map.of(
                "walletId", walletId,
                "transactions", transactions,
                "page", page,
                "size", size,
                "total", totalCount != null ? totalCount : 0
        ));
    }

    @GetMapping("/{walletId}/summary")
    @Operation(
            summary = "Get wallet summary statistics",
            description = "Retrieves aggregated statistics for a wallet"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Summary retrieved"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Wallet not found"
            )
    })
    public ResponseEntity<Map<String, Object>> getSummary(
            @Parameter(description = "Wallet identifier", required = true)
            @PathVariable String walletId) {
        
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                """
                SELECT wallet_id, total_deposits, total_withdrawals, 
                       total_transfers_in, total_transfers_out, current_balance, last_transaction_at
                FROM wallet_summary_view
                WHERE wallet_id = ?
                """,
                walletId
        );
        
        if (results.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> row = results.get(0);
        return ResponseEntity.ok(Map.of(
                "walletId", row.get("wallet_id"),
                "totalDeposits", row.get("total_deposits"),
                "totalWithdrawals", row.get("total_withdrawals"),
                "totalTransfersIn", row.get("total_transfers_in"),
                "totalTransfersOut", row.get("total_transfers_out"),
                "currentBalance", row.get("current_balance"),
                "lastTransactionAt", row.get("last_transaction_at")
        ));
    }
}

