package com.crablet.wallet.api;

import com.crablet.command.CommandExecutor;
import com.crablet.examples.wallet.commands.DepositCommand;
import com.crablet.examples.wallet.commands.OpenWalletCommand;
import com.crablet.examples.wallet.commands.TransferMoneyCommand;
import com.crablet.examples.wallet.commands.WithdrawCommand;
import com.crablet.wallet.api.dto.DepositRequest;
import com.crablet.wallet.api.dto.OpenWalletRequest;
import com.crablet.wallet.api.dto.TransferRequest;
import com.crablet.wallet.api.dto.WalletResponse;
import com.crablet.wallet.api.dto.WithdrawRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST API controller for wallet operations.
 * <p>
 * Provides endpoints for wallet commands (open, deposit, withdraw, transfer)
 * with full OpenAPI documentation.
 */
@RestController
@RequestMapping("/api/wallets")
@Tag(name = "Wallets", description = "Wallet management operations")
public class WalletController {

    private final CommandExecutor commandExecutor;

    public WalletController(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    @PostMapping
    @Operation(
            summary = "Open a new wallet",
            description = "Creates a new wallet with the specified owner and initial balance"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Wallet created successfully",
                    content = @Content(schema = @Schema(implementation = WalletResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Wallet already exists"
            )
    })
    public ResponseEntity<WalletResponse> openWallet(@Valid @RequestBody OpenWalletRequest request) {
        OpenWalletCommand command = OpenWalletCommand.of(
                request.walletId(),
                request.owner(),
                request.initialBalance()
        );
        
        var result = commandExecutor.executeCommand(command);
        
        WalletResponse response = new WalletResponse(
                request.walletId(),
                request.owner(),
                request.initialBalance(),
                java.time.Instant.now()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{walletId}/deposits")
    @Operation(
            summary = "Deposit money into a wallet",
            description = "Adds money to an existing wallet"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Deposit successful",
                    content = @Content(schema = @Schema(implementation = WalletResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Wallet not found"
            )
    })
    public ResponseEntity<WalletResponse> deposit(
            @Parameter(description = "Wallet identifier", required = true)
            @PathVariable String walletId,
            @Valid @RequestBody DepositRequest request) {
        
        DepositCommand command = DepositCommand.of(
                request.depositId(),
                walletId,
                request.amount(),
                request.description()
        );
        
        var result = commandExecutor.executeCommand(command);
        
        // Note: Balance would come from view projection in real implementation
        WalletResponse response = new WalletResponse(
                walletId,
                null, // Owner not available in deposit endpoint
                request.amount(), // Simplified - would query view for actual balance
                java.time.Instant.now()
        );
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{walletId}/withdrawals")
    @Operation(
            summary = "Withdraw money from a wallet",
            description = "Removes money from an existing wallet if sufficient funds are available"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Withdrawal successful",
                    content = @Content(schema = @Schema(implementation = WalletResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data or insufficient funds"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Wallet not found"
            )
    })
    public ResponseEntity<WalletResponse> withdraw(
            @Parameter(description = "Wallet identifier", required = true)
            @PathVariable String walletId,
            @Valid @RequestBody WithdrawRequest request) {
        
        WithdrawCommand command = WithdrawCommand.of(
                request.withdrawalId(),
                walletId,
                request.amount(),
                request.description()
        );
        
        var result = commandExecutor.executeCommand(command);
        
        // Note: Balance would come from view projection in real implementation
        WalletResponse response = new WalletResponse(
                walletId,
                null, // Owner not available in withdrawal endpoint
                0, // Simplified - would query view for actual balance
                java.time.Instant.now()
        );
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transfers")
    @Operation(
            summary = "Transfer money between wallets",
            description = "Transfers money from one wallet to another if source wallet has sufficient funds"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transfer successful"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data or insufficient funds"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Source or destination wallet not found"
            )
    })
    public ResponseEntity<Map<String, String>> transfer(@Valid @RequestBody TransferRequest request) {
        TransferMoneyCommand command = TransferMoneyCommand.of(
                request.transferId(),
                request.fromWalletId(),
                request.toWalletId(),
                request.amount(),
                request.description()
        );
        
        var result = commandExecutor.executeCommand(command);
        
        return ResponseEntity.ok(Map.of(
                "transferId", request.transferId(),
                "status", "completed"
        ));
    }
}

