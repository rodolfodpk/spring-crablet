package wallets.integration;

import com.crablet.integration.TestApplication;
import com.crablet.eventstore.ClockProvider;
import com.crablet.integration.AbstractCrabletIT;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for wallet application integration tests.
 * Extends Crablet framework test infrastructure and adds wallet-specific setup.
 * Uses no-outbox profile for faster domain-focused tests.
 */
@SpringBootTest(classes = com.crablet.integration.TestApplication.class, 
                webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = "spring.profiles.active=test")
@ActiveProfiles("test-no-outbox")
public abstract class AbstractWalletIntegrationTest extends AbstractCrabletIT {
    
    @Autowired
    private ClockProvider clock;
    
    @AfterEach
    void resetClock() {
        // Reset to system clock after each test to ensure clean state
        clock.resetToSystemClock();
    }
}
