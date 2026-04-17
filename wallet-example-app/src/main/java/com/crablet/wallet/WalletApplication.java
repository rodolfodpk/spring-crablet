package com.crablet.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Wallet Example Application
 *
 * <p>Complete example demonstrating Crablet event sourcing with:
 * <ul>
 *   <li>REST API with OpenAPI documentation</li>
 *   <li>Command handlers using DCB pattern</li>
 *   <li>View projections using crablet-views</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
    "com.crablet.wallet",
    "com.crablet.examples.wallet.commands"  // Required: scan handlers from shared-examples-domain
})
@EnableConfigurationProperties
public class WalletApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(WalletApplication.class, args);
    }
}

