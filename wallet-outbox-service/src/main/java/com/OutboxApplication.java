package com;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wallet Outbox Service Application
 * 
 * Microservice for reliable event publishing using the outbox pattern.
 * Reads events from PostgreSQL and publishes them to external systems.
 */
@SpringBootApplication(scanBasePackages = {"com.outbox", "com.crablet.outbox", "com.crablet.eventstore"})
@EnableScheduling
public class OutboxApplication {

    public static void main(String[] args) {
        SpringApplication.run(OutboxApplication.class, args);
    }
}
