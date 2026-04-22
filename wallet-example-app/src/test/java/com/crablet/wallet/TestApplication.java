package com.crablet.wallet;

import com.crablet.test.config.CrabletFlywayConfiguration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;

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
@Import(CrabletFlywayConfiguration.class)
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
    
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
