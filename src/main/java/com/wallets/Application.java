package com.wallets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
    "com.wallets", 
    "com.crablet.core", 
    "com.crablet.core.impl",
    "com.crablet.outbox"  // Add outbox package
})
@EnableConfigurationProperties  // Enable @ConfigurationProperties binding
@EnableScheduling  // Enable @Scheduled support
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
