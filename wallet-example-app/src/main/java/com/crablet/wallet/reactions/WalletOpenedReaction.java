package com.crablet.wallet.reactions;

import com.crablet.command.CommandExecutor;
import com.crablet.examples.notification.commands.SendWelcomeNotificationCommand;
import com.crablet.examples.wallet.events.WalletOpened;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Webhook endpoint for the "wallet-opened-welcome-notification" automation.
 *
 * <p>The automations module fires an HTTP POST here whenever a {@code WalletOpened}
 * event is dispatched. This controller extracts the wallet data from the payload
 * and executes a {@link SendWelcomeNotificationCommand}.
 */
@RestController
@RequestMapping("/api/automations")
public class WalletOpenedReaction {

    private static final Logger log = LoggerFactory.getLogger(WalletOpenedReaction.class);

    private final CommandExecutor commandExecutor;

    public WalletOpenedReaction(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    @PostMapping("/wallet-opened")
    public ResponseEntity<Void> onWalletOpened(@RequestBody Map<String, Object> payload) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            String walletId = stringValue(data, "wallet_id", "walletId");
            String owner = (String) data.get("owner");

            commandExecutor.execute(SendWelcomeNotificationCommand.of(walletId, owner));
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to process wallet-opened webhook: {}", e.getMessage(), e);
            throw new RuntimeException("WalletOpenedReaction failed", e);
        }
    }

    private String stringValue(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value instanceof String string && !string.isBlank()) {
                return string;
            }
        }
        return null;
    }
}
