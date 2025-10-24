package wallets.integration;

import com.Application;
import com.crablet.core.ClockProvider;
import crablet.integration.AbstractCrabletIT;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for wallet application integration tests with outbox enabled.
 * Extends Crablet framework test infrastructure and adds wallet-specific setup.
 * Use this base class when testing outbox event publishing behavior.
 */
@SpringBootTest(classes = Application.class, 
                webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = "spring.profiles.active=test")
@ActiveProfiles("test")  // Uses default test profile with outbox enabled
public abstract class AbstractWalletIntegrationTestWithOutbox extends AbstractCrabletIT {
    
    @Autowired
    private ClockProvider clock;
    
    @AfterEach
    void resetClock() {
        // Reset to system clock after each test to ensure clean state
        clock.resetToSystemClock();
    }
}

