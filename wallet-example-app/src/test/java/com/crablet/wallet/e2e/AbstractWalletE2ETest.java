package com.crablet.wallet.e2e;

import com.crablet.wallet.AbstractWalletTest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Base class for E2E tests that hit the HTTP API.
 * <p>
 * Provides:
 * <ul>
 *   <li>WebTestClient configured with random port</li>
 *   <li>Helper methods for API calls</li>
 *   <li>View verification utilities</li>
 * </ul>
 */
public abstract class AbstractWalletE2ETest extends AbstractWalletTest {
    
    @LocalServerPort
    protected int port;
    
    protected WebTestClient webTestClient;
    
    @BeforeEach
    @Override
    protected void setUp() {
        // Don't call super.setUp() here - E2E tests with @Order need to preserve state between tests
        // Individual test classes should clean database only in @Order(1) tests
        // Configure WebTestClient with actual port when using RANDOM_PORT
        webTestClient = WebTestClient
            .bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
    }
}

