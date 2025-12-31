package com.crablet.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Test application context for wallets-example-app integration tests.
 * <p>
 * This class sets up the Spring Boot application context for testing,
 * ensuring all beans are properly wired and the application can start.
 */
@SpringBootApplication
@ComponentScan(
    basePackages = {"com.crablet.wallet", "com.crablet"}
)
public class TestApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}

