package com.crablet.wallet.reactions;

import com.crablet.command.CommandExecutor;
import com.crablet.examples.notification.commands.SendWelcomeNotificationCommand;
import com.crablet.examples.wallet.events.WalletEvent;
import com.crablet.examples.wallet.events.WalletOpened;
import com.crablet.automations.AutomationHandler;
import com.crablet.eventstore.store.StoredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Automation: when a wallet is opened, send a welcome notification.
 *
 * <p>Demonstrates the automation → command → event chain:
 * {@code WalletOpened} event triggers {@link SendWelcomeNotificationCommand},
 * which records a {@code WelcomeNotificationSent} event.
 */
@Component
public class WalletOpenedReaction implements AutomationHandler {

    private static final Logger log = LoggerFactory.getLogger(WalletOpenedReaction.class);
    private static final String AUTOMATION_NAME = "wallet-opened-welcome-notification";

    private final ObjectMapper objectMapper;

    public WalletOpenedReaction(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getAutomationName() {
        return AUTOMATION_NAME;
    }

    @Override
    public void react(StoredEvent event, CommandExecutor commandExecutor) {
        try {
            WalletEvent walletEvent = objectMapper.readValue(event.data(), WalletEvent.class);
            if (walletEvent instanceof WalletOpened opened) {
                commandExecutor.executeCommand(
                        SendWelcomeNotificationCommand.of(opened.walletId(), opened.owner())
                );
            }
        } catch (Exception e) {
            log.error("Failed to process WalletOpened reaction for event position={}", event.position(), e);
            throw new RuntimeException("WalletOpenedReaction failed", e);
        }
    }
}
