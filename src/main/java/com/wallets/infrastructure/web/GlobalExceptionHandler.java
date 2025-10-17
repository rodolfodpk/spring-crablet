package com.wallets.infrastructure.web;

import com.crablet.core.Command;
import com.crablet.core.ConcurrencyException;
import com.wallets.domain.WalletCommand;
import com.wallets.domain.exception.DuplicateOperationException;
import com.wallets.domain.exception.InsufficientFundsException;
import com.wallets.domain.exception.InvalidOperationException;
import com.wallets.domain.exception.OptimisticLockException;
import com.wallets.domain.exception.WalletAlreadyExistsException;
import com.wallets.domain.exception.WalletNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST API endpoints.
 * Maps domain exceptions to appropriate HTTP status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle wallet already exists - return 200 OK for idempotent operation.
     */
    @ExceptionHandler(WalletAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleWalletAlreadyExists(WalletAlreadyExistsException ex) {
        Map<String, Object> body = Map.of(
            "message", ex.getMessage(),
            "walletId", ex.getWalletId(),
            "timestamp", Instant.now().toString()
        );
        return ResponseEntity.ok(body);
    }

    /**
     * Handle wallet not found - return 404 NOT FOUND.
     */
    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleWalletNotFound(WalletNotFoundException ex) {
        log.warn("Wallet not found: walletId={}", ex.getWalletId());
        Map<String, Object> body = Map.of(
            "error", Map.of(
                "code", "WALLET_NOT_FOUND",
                "message", ex.getMessage(),
                "walletId", ex.getWalletId(),
                "timestamp", Instant.now().toString()
            )
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Handle insufficient funds - return 400 BAD REQUEST.
     */
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientFunds(InsufficientFundsException ex) {
        log.warn("Insufficient funds: walletId={}, balance={}, requested={}", 
            ex.getWalletId(), ex.getCurrentBalance(), ex.getRequestedAmount());
        Map<String, Object> body = Map.of(
            "error", Map.of(
                "code", "INSUFFICIENT_FUNDS",
                "message", ex.getMessage(),
                "walletId", ex.getWalletId(),
                "currentBalance", ex.getCurrentBalance(),
                "requestedAmount", ex.getRequestedAmount(),
                "timestamp", Instant.now().toString()
            )
        );
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handle invalid operation - return 400 BAD REQUEST.
     */
    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidOperation(InvalidOperationException ex) {
        Map<String, Object> body = Map.of(
            "error", Map.of(
                "code", "VALIDATION_ERROR",
                "message", ex.getMessage(),
                "operation", ex.getOperation(),
                "reason", ex.getReason(),
                "timestamp", Instant.now().toString()
            )
        );
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handle duplicate operations - return 200 OK for idempotent operation.
     */
    @ExceptionHandler(DuplicateOperationException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateOperation(DuplicateOperationException ex) {
        Map<String, Object> body = Map.of(
            "message", ex.getMessage(),
            "timestamp", Instant.now().toString()
        );
        return ResponseEntity.ok(body);
    }

    /**
     * Handle optimistic lock failures - return 409 CONFLICT.
     */
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockException ex) {
        Map<String, Object> body = Map.of(
            "error", Map.of(
                "code", "OPTIMISTIC_LOCK_FAILED",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
            )
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Handle concurrency conflicts - return 409 CONFLICT.
     */
    @ExceptionHandler(ConcurrencyException.class)
    public ResponseEntity<Map<String, Object>> handleConcurrencyConflict(ConcurrencyException ex) {
        Command cmd = ex.getCommand();
        if (cmd == null) {
            // Fallback for old-style ConcurrencyException without command
            log.warn("Concurrency conflict: {}", ex.getMessage());
            return handleGenericConcurrencyConflict(ex);
        }
        
        String cmdType = cmd.getCommandType();
        log.warn("Concurrency conflict for command: type={}, message={}", cmdType, ex.getMessage());
        String msg = ex.getMessage().toLowerCase();
        
        // Pattern 1: Duplicate entity IDs (idempotency violations)
        if (cmdType.equals("open_wallet")) {
            return handleWalletAlreadyExists(new WalletAlreadyExistsException(extractWalletId(cmd)));
        }
        if (cmdType.equals("deposit")) {
            return handleDuplicateOperation(new DuplicateOperationException("Deposit already processed: " + extractDepositId(cmd)));
        }
        if (cmdType.equals("withdraw")) {
            return handleDuplicateOperation(new DuplicateOperationException("Withdrawal already processed: " + extractWithdrawalId(cmd)));
        }
        if (cmdType.equals("transfer_money")) {
            return handleDuplicateOperation(new DuplicateOperationException("Transfer already processed: " + extractTransferId(cmd)));
        }
        
        // Pattern 2: Optimistic concurrency control failures
        if (msg.contains("cursor") || msg.contains("position") || msg.contains("aftercursor")) {
            return handleOptimisticLock(new OptimisticLockException("Wallet state changed during operation, please retry"));
        }
        
        // Pattern 3: Unknown failure - use generic handler
        return handleGenericConcurrencyConflict(ex);
    }

    /**
     * Handle illegal arguments - return 400 BAD REQUEST.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> body = Map.of(
            "error", Map.of(
                "code", "VALIDATION_ERROR",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
            )
        );
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handle no handler found - return 404 NOT FOUND.
     */
    @ExceptionHandler(org.springframework.web.servlet.NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(org.springframework.web.servlet.NoHandlerFoundException ex) {
        Map<String, Object> body = Map.of(
            "error", Map.of(
                "code", "NOT_FOUND",
                "message", "No handler found for " + ex.getHttpMethod() + " " + ex.getRequestURL(),
                "timestamp", Instant.now().toString()
            )
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Handle no resource found - return 404 NOT FOUND.
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        Map<String, Object> body = Map.of(
            "error", Map.of(
                "code", "NOT_FOUND",
                "message", "Resource not found: " + ex.getHttpMethod() + " " + ex.getResourcePath(),
                "timestamp", Instant.now().toString()
            )
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Handle generic exceptions - return 500 INTERNAL SERVER ERROR.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception occurred: {}", ex.getMessage(), ex);
        Map<String, Object> body = Map.of(
            "error", Map.of(
                "code", "INTERNAL_ERROR",
                "message", "An unexpected error occurred",
                "timestamp", Instant.now().toString()
            )
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
    
    // Helper methods for command-specific exception mapping using polymorphic methods
    
    private String extractWalletId(Command cmd) {
        if (cmd instanceof WalletCommand wc) {
            return wc.getWalletId();
        }
        throw new IllegalStateException("Cannot extract wallet_id from " + cmd.getClass());
    }

    private String extractDepositId(Command cmd) {
        if (cmd instanceof WalletCommand wc) {
            return wc.getOperationId();
        }
        throw new IllegalStateException("Cannot extract deposit_id from " + cmd.getClass());
    }

    private String extractWithdrawalId(Command cmd) {
        if (cmd instanceof WalletCommand wc) {
            return wc.getOperationId();
        }
        throw new IllegalStateException("Cannot extract withdrawal_id from " + cmd.getClass());
    }

    private String extractTransferId(Command cmd) {
        if (cmd instanceof WalletCommand wc) {
            return wc.getOperationId();
        }
        throw new IllegalStateException("Cannot extract transfer_id from " + cmd.getClass());
    }

    private ResponseEntity<Map<String, Object>> handleGenericConcurrencyConflict(ConcurrencyException ex) {
        Map<String, Object> body = Map.of(
            "error", Map.of(
                "code", "CONCURRENCY_CONFLICT",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
            )
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Handle Bean Validation errors - return 400 BAD REQUEST with field details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
        MethodArgumentNotValidException ex) {
        
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Validation failed");
        response.put("message", "Invalid request parameters");
        response.put("fields", fieldErrors);
        response.put("timestamp", Instant.now().toString());
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }

}
