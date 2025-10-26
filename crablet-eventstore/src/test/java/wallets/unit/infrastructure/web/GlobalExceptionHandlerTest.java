package wallets.unit.infrastructure.web;
import wallets.integration.AbstractWalletIntegrationTest;

import com.crablet.eventstore.ConcurrencyException;
import com.wallets.domain.exception.InsufficientFundsException;
import com.wallets.domain.exception.InvalidOperationException;
import com.wallets.domain.exception.WalletAlreadyExistsException;
import com.wallets.domain.exception.WalletNotFoundException;
import com.wallets.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for GlobalExceptionHandler to ensure proper HTTP status codes
 * and error response formats for all exception types.
 */
@DisplayName("GlobalExceptionHandler Integration Tests")
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("Should handle WalletAlreadyExistsException with 200 OK")
    void shouldHandleWalletAlreadyExistsWith200Ok() {
        // Given
        String walletId = "wallet-123";
        WalletAlreadyExistsException ex = new WalletAlreadyExistsException(walletId);

        // When
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleWalletAlreadyExists(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("message")).isEqualTo("Wallet already exists: " + walletId);
        assertThat(response.getBody().get("walletId")).isEqualTo(walletId);
        assertThat(response.getBody().get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Should handle WalletNotFoundException with 404 NOT FOUND")
    void shouldHandleWalletNotFoundWith404NotFound() {
        // Given
        String walletId = "wallet-123";
        WalletNotFoundException ex = new WalletNotFoundException(walletId);

        // When
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleWalletNotFound(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("WALLET_NOT_FOUND");
        assertThat(error.get("message")).isEqualTo("Wallet not found: " + walletId);
        assertThat(error.get("walletId")).isEqualTo(walletId);
        assertThat(error.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Should handle InsufficientFundsException with 400 BAD REQUEST")
    void shouldHandleInsufficientFundsWith400BadRequest() {
        // Given
        String walletId = "wallet-123";
        int currentBalance = 100;
        int requestedAmount = 200;
        InsufficientFundsException ex = new InsufficientFundsException(walletId, currentBalance, requestedAmount);

        // When
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleInsufficientFunds(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(error.get("message")).isEqualTo("Insufficient funds in wallet wallet-123: balance 100, requested 200");
        assertThat(error.get("walletId")).isEqualTo(walletId);
        assertThat(error.get("currentBalance")).isEqualTo(currentBalance);
        assertThat(error.get("requestedAmount")).isEqualTo(requestedAmount);
        assertThat(error.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Should handle InvalidOperationException with 400 BAD REQUEST")
    void shouldHandleInvalidOperationWith400BadRequest() {
        // Given
        InvalidOperationException ex = new InvalidOperationException("test-operation", "test-reason");

        // When
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleInvalidOperation(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("VALIDATION_ERROR");
        assertThat(error.get("message")).isEqualTo("Invalid test-operation operation: test-reason");
        assertThat(error.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Should handle ConcurrencyException with 409 CONFLICT")
    void shouldHandleConcurrencyExceptionWith409Conflict() {
        // Given
        String message = "Concurrency conflict";
        ConcurrencyException ex = new ConcurrencyException(message);

        // When
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleConcurrencyConflict(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("CONCURRENCY_CONFLICT");
        assertThat(error.get("message")).isEqualTo(message);
        assertThat(error.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Should handle NoHandlerFoundException with 404 NOT FOUND")
    void shouldHandleNoHandlerFoundWith404NotFound() {
        // Given
        NoHandlerFoundException ex = new NoHandlerFoundException("GET", "/invalid-endpoint", null);

        // When
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleNoHandlerFound(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("NOT_FOUND");
        assertThat(error.get("message")).isEqualTo("No handler found for GET /invalid-endpoint");
        assertThat(error.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Should handle generic Exception with 500 INTERNAL SERVER ERROR")
    void shouldHandleGenericExceptionWith500InternalServerError() {
        // Given
        String message = "Unexpected error";
        Exception ex = new RuntimeException(message);

        // When
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleGenericException(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("INTERNAL_ERROR");
        assertThat(error.get("message")).isEqualTo("An unexpected error occurred");
        assertThat(error.get("timestamp")).isNotNull();
    }
}
