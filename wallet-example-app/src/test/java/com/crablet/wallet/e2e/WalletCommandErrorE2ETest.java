package com.crablet.wallet.e2e;

import com.crablet.wallet.TestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E tests for error handling in the wallet HTTP API.
 * Verifies that domain exceptions are mapped to correct HTTP status codes.
 */
@SpringBootTest(
    classes = TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=test",
        "spring.main.allow-bean-definition-overriding=true"
    }
)
@DisplayName("Wallet Command Error Handling E2E Tests")
class WalletCommandErrorE2ETest extends AbstractWalletE2ETest {

    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();      // sets up WebTestClient
        cleanDatabase();    // each test starts with a clean slate
    }

    @Test
    @DisplayName("Should return 404 when querying a non-existent wallet")
    void shouldReturn404WhenWalletNotFound() {
        webTestClient
            .get().uri("/api/wallets/does-not-exist")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody(Map.class)
            .value(body -> {
                assertThat(body.get("title")).isEqualTo("Wallet Not Found");
                assertThat(body.get("status")).isEqualTo(404);
            });
    }

    @Test
    @DisplayName("Should return 400 when withdrawing more than the available balance")
    void shouldReturn400WhenInsufficientFunds() {
        String walletId = "wallet-err-insuf-1";

        // Open with balance=50
        webTestClient
            .post().uri("/api/commands")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"commandType":"open_wallet","walletId":"%s","owner":"Alice","initialBalance":50}
                """.formatted(walletId))
            .exchange()
            .expectStatus().isCreated();

        // Try to withdraw 200 — exceeds balance
        webTestClient
            .post().uri("/api/commands")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"commandType":"withdraw","withdrawalId":"w-err-1","walletId":"%s","amount":200,"description":"Too much"}
                """.formatted(walletId))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody(Map.class)
            .value(body -> {
                assertThat(body.get("title")).isEqualTo("Insufficient Funds");
                assertThat(body.get("status")).isEqualTo(400);
                assertThat(((Number) body.get("currentBalance")).intValue()).isEqualTo(50);
                assertThat(((Number) body.get("requestedAmount")).intValue()).isEqualTo(200);
            });
    }

    @Test
    @DisplayName("Should return 400 when depositing to a non-existent wallet")
    void shouldReturn400WhenDepositingToNonExistentWallet() {
        webTestClient
            .post().uri("/api/commands")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"commandType":"deposit","depositId":"dep-ghost-1","walletId":"ghost-wallet","amount":100,"description":"Test"}
                """)
            .exchange()
            .expectStatus().is4xxClientError();
    }

    @Test
    @DisplayName("Should return 400 when withdrawing from a non-existent wallet")
    void shouldReturn400WhenWithdrawingFromNonExistentWallet() {
        webTestClient
            .post().uri("/api/commands")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"commandType":"withdraw","withdrawalId":"w-ghost-1","walletId":"ghost-wallet","amount":100,"description":"Test"}
                """)
            .exchange()
            .expectStatus().is4xxClientError();
    }

    @Test
    @DisplayName("Should create wallet successfully with zero initial balance")
    void shouldCreateWalletWithZeroBalance() {
        webTestClient
            .post().uri("/api/commands")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"commandType":"open_wallet","walletId":"wallet-zero-1","owner":"Bob","initialBalance":0}
                """)
            .exchange()
            .expectStatus().isCreated();
    }
}
