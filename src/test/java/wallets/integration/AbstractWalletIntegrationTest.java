package wallets.integration;

import com.Application;
import com.crablet.core.ClockProvider;
import crablet.integration.AbstractCrabletIT;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;

/**
 * Base class for wallet application integration tests.
 * Extends Crablet framework test infrastructure and adds wallet-specific setup.
 */
@SpringBootTest(classes = Application.class, 
                webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = "spring.profiles.active=test")
public abstract class AbstractWalletIntegrationTest extends AbstractCrabletIT {
    
    @Autowired
    private ClockProvider clock;
    
    @AfterEach
    void resetClock() {
        // Reset to system clock after each test to ensure clean state
        clock.resetToSystemClock();
    }
}
