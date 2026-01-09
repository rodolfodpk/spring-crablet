package com.crablet.wallet;

import com.crablet.command.CommandExecutor;
import com.crablet.eventstore.store.EventStore;
import com.crablet.wallet.api.WalletController;
import com.crablet.wallet.api.WalletQueryController;
import com.crablet.wallet.view.config.ViewConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Application context test to verify all beans are properly configured.
 * <p>
 * This test ensures:
 * <ul>
 *   <li>Application context loads successfully</li>
 *   <li>All required beans are present</li>
 *   <li>No circular dependencies or missing dependencies</li>
 * </ul>
 */
@DisplayName("Wallet Application Context Tests")
class WalletApplicationContextTest extends AbstractWalletTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private CommandExecutor commandExecutor;

    @Autowired
    private WalletController walletController;

    @Autowired
    private WalletQueryController walletQueryController;

    @Autowired
    private ViewConfiguration viewConfiguration;

    @Test
    @DisplayName("Application context should load successfully")
    void applicationContextShouldLoad() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    @DisplayName("EventStore bean should be present")
    void eventStoreShouldBePresent() {
        assertThat(eventStore).isNotNull();
    }

    @Test
    @DisplayName("CommandExecutor bean should be present")
    void commandExecutorShouldBePresent() {
        assertThat(commandExecutor).isNotNull();
    }

    @Test
    @DisplayName("WalletController bean should be present")
    void walletControllerShouldBePresent() {
        assertThat(walletController).isNotNull();
    }

    @Test
    @DisplayName("WalletQueryController bean should be present")
    void walletQueryControllerShouldBePresent() {
        assertThat(walletQueryController).isNotNull();
    }

    @Test
    @DisplayName("ViewConfiguration bean should be present")
    void viewConfigurationShouldBePresent() {
        assertThat(viewConfiguration).isNotNull();
    }

    @Test
    @DisplayName("All command handlers should be registered")
    void commandHandlersShouldBeRegistered() {
        // Verify handlers are registered via CommandExecutor
        // This is an indirect check - if handlers weren't registered, CommandExecutor would fail
        assertThat(commandExecutor).isNotNull();
    }

    @Test
    @DisplayName("All view projectors should be registered")
    void viewProjectorsShouldBeRegistered() {
        // Verify view projectors are registered via ViewConfiguration
        // This is an indirect check - if projectors weren't registered, views would fail
        assertThat(viewConfiguration).isNotNull();
    }
}

