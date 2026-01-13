package com.crablet.wallet.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E tests for ViewController REST endpoints.
 * Tests all HTTP endpoints using WebTestClient with full Spring Boot context.
 * <p>
 * Tests cover:
 * <ul>
 *   <li>Status endpoints (get status, get all statuses)</li>
 *   <li>Operation endpoints (pause, resume, reset)</li>
 *   <li>Detailed progress endpoints (get details, get all details)</li>
 *   <li>Integration scenarios (operations + details)</li>
 * </ul>
 */
@SpringBootTest(
    classes = com.crablet.wallet.WalletApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@DisplayName("ViewController E2E Tests")
class ViewControllerE2ETest extends AbstractWalletE2ETest {

    // ========== Status Endpoints ==========

    @Test
    @DisplayName("Given existing view, when getting status, then returns status with lag")
    void givenExistingView_whenGettingStatus_thenReturnsStatusWithLag() {
        // Given
        String viewName = "wallet-balance-view";

        // When & Then
        webTestClient.get()
            .uri("/api/views/{viewName}/status", viewName)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("wallet-balance-view")
            .jsonPath("$.status").exists()
            .jsonPath("$.lag").exists();
    }

    @Test
    @DisplayName("Given non-existent view, when getting status, then returns 404")
    void givenNonExistentView_whenGettingStatus_thenReturns404() {
        // Given
        String viewName = "non-existent-view";

        // When & Then
        webTestClient.get()
            .uri("/api/views/{viewName}/status", viewName)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("Given multiple views, when getting all statuses, then returns all views")
    void givenMultipleViews_whenGettingAllStatuses_thenReturnsAllViews() {
        // When & Then
        webTestClient.get()
            .uri("/api/views/status")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.wallet-balance-view").exists()
            .jsonPath("$.wallet-transaction-view").exists()
            .jsonPath("$.wallet-summary-view").exists()
            .jsonPath("$.wallet-statement-view").exists();
    }

    // ========== Operation Endpoints ==========

    @Test
    @DisplayName("Given active view, when pausing, then returns paused status")
    void givenActiveView_whenPausing_thenReturnsPausedStatus() {
        // Given
        String viewName = "wallet-balance-view";

        // When & Then
        webTestClient.post()
            .uri("/api/views/{viewName}/pause", viewName)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("wallet-balance-view")
            .jsonPath("$.status").isEqualTo("PAUSED")
            .jsonPath("$.message").isEqualTo("View projection paused successfully");
    }

    @Test
    @DisplayName("Given paused view, when resuming, then returns active status")
    void givenPausedView_whenResuming_thenReturnsActiveStatus() {
        // Given
        String viewName = "wallet-transaction-view";
        // First pause the view
        webTestClient.post()
            .uri("/api/views/{viewName}/pause", viewName)
            .exchange()
            .expectStatus().isOk();

        // When & Then
        webTestClient.post()
            .uri("/api/views/{viewName}/resume", viewName)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("wallet-transaction-view")
            .jsonPath("$.status").isEqualTo("ACTIVE")
            .jsonPath("$.message").isEqualTo("View projection resumed successfully");
    }

    @Test
    @DisplayName("Given existing view, when resetting, then returns active status")
    void givenExistingView_whenResetting_thenReturnsActiveStatus() {
        // Given
        String viewName = "wallet-summary-view";

        // When & Then
        webTestClient.post()
            .uri("/api/views/{viewName}/reset", viewName)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("wallet-summary-view")
            .jsonPath("$.status").isEqualTo("ACTIVE")
            .jsonPath("$.message").isEqualTo("View projection reset successfully");
    }

    @Test
    @DisplayName("Given non-existent view, when pausing, then returns 400")
    void givenNonExistentView_whenPausing_thenReturns400() {
        // Given
        String viewName = "non-existent-view";

        // When & Then - pause returns false for non-existent views, causing 400
        webTestClient.post()
            .uri("/api/views/{viewName}/pause", viewName)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("non-existent-view")
            .jsonPath("$.message").isEqualTo("Failed to pause view projection");
    }

    @Test
    @DisplayName("Given non-existent view, when resuming, then returns 400")
    void givenNonExistentView_whenResuming_thenReturns400() {
        // Given
        String viewName = "non-existent-view";

        // When & Then - resume returns false for non-existent views, causing 400
        webTestClient.post()
            .uri("/api/views/{viewName}/resume", viewName)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("non-existent-view")
            .jsonPath("$.message").isEqualTo("Failed to resume view projection");
    }

    @Test
    @DisplayName("Given non-existent view, when resetting, then returns 400")
    void givenNonExistentView_whenResetting_thenReturns400() {
        // Given
        String viewName = "non-existent-view";

        // When & Then - reset returns false for non-existent views, causing 400
        webTestClient.post()
            .uri("/api/views/{viewName}/reset", viewName)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("non-existent-view")
            .jsonPath("$.message").isEqualTo("Failed to reset view projection");
    }

    // ========== Detailed Progress Endpoints ==========

    @Test
    @DisplayName("Given existing view, when getting details, then returns all fields")
    void givenExistingView_whenGettingDetails_thenReturnsAllFields() {
        // Given
        String viewName = "wallet-balance-view";

        // When & Then
        webTestClient.get()
            .uri("/api/views/{viewName}/details", viewName)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("wallet-balance-view")
            .jsonPath("$.status").exists()
            .jsonPath("$.lastPosition").exists()
            .jsonPath("$.errorCount").exists()
            .jsonPath("$.lastUpdatedAt").exists()
            .jsonPath("$.createdAt").exists();
    }

    @Test
    @DisplayName("Given view, when getting details, then shows error info (may be null if no errors)")
    void givenView_whenGettingDetails_thenShowsErrorInfo() {
        // Given - View exists
        String viewName = "wallet-statement-view";

        // When & Then - Error fields may be null if no errors, which is valid
        // We just verify the endpoint works and returns the expected structure
        webTestClient.get()
            .uri("/api/views/{viewName}/details", viewName)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("wallet-statement-view")
            .jsonPath("$.errorCount").exists()
            // lastError and lastErrorAt are optional fields (may be null)
            .jsonPath("$.lastError").value(value -> {
                // Value can be null or a string - both are valid
                assertThat(value == null || value instanceof String).isTrue();
            })
            .jsonPath("$.lastErrorAt").value(value -> {
                // Value can be null or a timestamp - both are valid
                assertThat(value == null || value instanceof String).isTrue();
            });
    }

    @Test
    @DisplayName("Given non-existent view, when getting details, then returns 404")
    void givenNonExistentView_whenGettingDetails_thenReturns404() {
        // Given
        String viewName = "non-existent-view";

        // When & Then
        webTestClient.get()
            .uri("/api/views/{viewName}/details", viewName)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("Given multiple views, when getting all details, then returns all views")
    void givenMultipleViews_whenGettingAllDetails_thenReturnsAllViews() {
        // When & Then
        webTestClient.get()
            .uri("/api/views/details")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.wallet-balance-view").exists()
            .jsonPath("$.wallet-transaction-view").exists()
            .jsonPath("$.wallet-summary-view").exists()
            .jsonPath("$.wallet-statement-view").exists();
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("Given view, when pausing and getting details, then details reflect paused status")
    void givenView_whenPausingAndGettingDetails_thenDetailsReflectPausedStatus() {
        // Given
        String viewName = "wallet-balance-view";

        // When - Pause the view
        webTestClient.post()
            .uri("/api/views/{viewName}/pause", viewName)
            .exchange()
            .expectStatus().isOk();

        // Then - Get details and verify status
        webTestClient.get()
            .uri("/api/views/{viewName}/details", viewName)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("wallet-balance-view")
            .jsonPath("$.status").isEqualTo("PAUSED");
    }

    @Test
    @DisplayName("Given view, when resetting and getting details, then details reflect reset")
    void givenView_whenResettingAndGettingDetails_thenDetailsReflectReset() {
        // Given
        String viewName = "wallet-transaction-view";

        // When - Reset the view
        webTestClient.post()
            .uri("/api/views/{viewName}/reset", viewName)
            .exchange()
            .expectStatus().isOk();

        // Then - Get details and verify status is ACTIVE
        webTestClient.get()
            .uri("/api/views/{viewName}/details", viewName)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("wallet-transaction-view")
            .jsonPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Given all views, when getting all details, then matches all statuses")
    void givenAllViews_whenGettingAllDetails_thenMatchesAllStatuses() {
        // When - Get all statuses
        var statusesResponse = webTestClient.get()
            .uri("/api/views/status")
            .exchange()
            .expectStatus().isOk()
            .returnResult(Map.class)
            .getResponseBody()
            .blockFirst();

        // And - Get all details
        var detailsResponse = webTestClient.get()
            .uri("/api/views/details")
            .exchange()
            .expectStatus().isOk()
            .returnResult(Map.class)
            .getResponseBody()
            .blockFirst();

        // Then - Both should contain the same view names
        assertThat(statusesResponse).isNotNull();
        assertThat(detailsResponse).isNotNull();
        assertThat(statusesResponse).containsKey("wallet-balance-view");
        assertThat(detailsResponse).containsKey("wallet-balance-view");
    }

    // ========== Lag Endpoint ==========

    @Test
    @DisplayName("Given existing view, when getting lag, then returns lag value")
    void givenExistingView_whenGettingLag_thenReturnsLagValue() {
        // Given
        String viewName = "wallet-balance-view";

        // When & Then
        webTestClient.get()
            .uri("/api/views/{viewName}/lag", viewName)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("wallet-balance-view")
            .jsonPath("$.lag").exists();
    }

    @Test
    @DisplayName("Given non-existent view, when getting lag, then returns 404")
    void givenNonExistentView_whenGettingLag_thenReturns404() {
        // Given
        String viewName = "non-existent-view";

        // When & Then
        webTestClient.get()
            .uri("/api/views/{viewName}/lag", viewName)
            .exchange()
            .expectStatus().isNotFound();
    }
}
