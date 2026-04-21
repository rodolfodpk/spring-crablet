package com.crablet.wallet.automations;

import com.crablet.automations.AutomationHandler;
import com.crablet.command.CommandExecutor;
import com.crablet.eventstore.EventType;
import com.crablet.eventstore.StoredEvent;
import com.crablet.examples.wallet.notification.commands.SendWelcomeNotificationCommand;
import com.crablet.examples.wallet.events.WalletOpened;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

/**
 * In-process automation handler for the "wallet-opened-welcome-notification" automation.
 *
 * <p>Called directly by the automation processor whenever a {@code WalletOpened} event
 * is detected. Deserializes the event and executes a {@link SendWelcomeNotificationCommand}.
 * No HTTP round-trip involved.
 */
@Component
public class WalletOpenedAutomation implements AutomationHandler {

    private static final Logger log = LoggerFactory.getLogger(WalletOpenedAutomation.class);

    private final ObjectMapper objectMapper;

    public WalletOpenedAutomation(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getAutomationName() {
        return "wallet-opened-welcome-notification";
    }

    @Override
    public Set<String> getEventTypes() {
        return Set.of(EventType.type(WalletOpened.class));
    }

    @Override
    public void react(StoredEvent event, CommandExecutor commandExecutor) {
        try {
            WalletOpened opened = objectMapper.readValue(event.data(), WalletOpened.class);
            commandExecutor.execute(SendWelcomeNotificationCommand.of(opened.walletId(), opened.owner()));
            log.info("Sent welcome notification for wallet: {}", opened.walletId());
        } catch (Exception e) {
            log.error("Failed to process WalletOpened event position={}: {}", event.position(), e.getMessage(), e);
            throw new RuntimeException("WalletOpenedAutomation failed", e);
        }
    }
}
