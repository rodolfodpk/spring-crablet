package com.crablet.wallet;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import javax.sql.DataSource;
import java.time.Instant;

/**
 * Test application context for wallet-example-app integration tests.
 * <p>
 * This class sets up the Spring Boot application context for testing,
 * ensuring all beans are properly wired and the application can start.
 * <p>
 * Spring Boot provides the base {@link DataSource}; Crablet auto-configuration
 * adds the framework's read/write datasource beans around it.
 */
@SpringBootApplication
@ComponentScan(
    basePackages = {"com.crablet.wallet", "com.crablet"},
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ANNOTATION,
            classes = AutoConfiguration.class
        )
    }
)
public class TestApplication {

    /**
     * ObjectMapper bean for JSON serialization.
     * Registers Java 8 time module for Instant, LocalDateTime, etc.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = JsonMapper.builder().build();
        return mapper;
    }
    
    /**
     * Flyway bean to ensure migrations run before tests.
     * Migrations run immediately when bean is created.
     * Uses migrations from src/main/resources/db/migration.
     */
    @Bean
    public Flyway flyway(DataSource dataSource) {
        Logger log = LoggerFactory.getLogger(TestApplication.class);
        log.info("[TestApplication] Flyway bean creation started at {}", Instant.now());
        
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        
        log.info("[TestApplication] Starting Flyway migration at {}", Instant.now());
        flyway.migrate();
        log.info("[TestApplication] Flyway migration completed at {}", Instant.now());
        
        return flyway;
    }
    
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
