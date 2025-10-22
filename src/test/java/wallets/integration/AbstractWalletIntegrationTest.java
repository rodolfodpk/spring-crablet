package wallets.integration;

import com.wallets.Application;
import crablet.integration.AbstractCrabletTest;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Base class for wallet application integration tests.
 * Extends Crablet framework test infrastructure and adds wallet-specific setup.
 */
@SpringBootTest(classes = Application.class, 
                webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = "spring.profiles.active=test")
public abstract class AbstractWalletIntegrationTest extends AbstractCrabletTest {
    // Wallet-specific test utilities can be added here
}
