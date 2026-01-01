package com.crablet.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Test application context for wallets-example-app integration tests.
 * <p>
 * This class sets up the Spring Boot application context for testing,
 * ensuring all beans are properly wired and the application can start.
 * <p>
 * Note: Excludes DataSourceConfig from component scan to avoid Spring Boot 4.0.1
 * auto-configuration bug that references removed DataSourceProperties class.
 * The DataSource is provided by Spring Boot's auto-configuration instead.
 */
@SpringBootApplication
@EnableConfigurationProperties
@ComponentScan(
    basePackages = {"com.crablet.wallet", "com.crablet"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.crablet\\.eventstore\\.config\\.DataSourceConfig"
    )
)
public class TestApplication {
    
    /**
     * Compatibility bean for Spring Boot 4.0.1 auto-configuration bug.
     * Provides DataSourceProperties bean that auto-configuration expects.
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource")
    public org.springframework.boot.autoconfigure.jdbc.DataSourceProperties dataSourceProperties() {
        return new org.springframework.boot.autoconfigure.jdbc.DataSourceProperties();
    }
    
    /**
     * Primary DataSource bean (required by crablet-views).
     * Since we exclude DataSourceConfig, we need to provide this manually.
     */
    @Bean(name = "primaryDataSource")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource primaryDataSource(org.springframework.boot.autoconfigure.jdbc.DataSourceProperties properties) {
        return DataSourceBuilder.create()
            .type(HikariDataSource.class)
            .url(properties.getUrl())
            .username(properties.getUsername())
            .password(properties.getPassword())
            .driverClassName(properties.getDriverClassName())
            .build();
    }
    
    /**
     * Read DataSource bean (required by crablet-views).
     * For this example app, we use the same DataSource for reads and writes.
     */
    @Bean(name = "readDataSource")
    public DataSource readDataSource(@org.springframework.beans.factory.annotation.Qualifier("primaryDataSource") DataSource primaryDataSource) {
        return primaryDataSource;
    }
    
    /**
     * ObjectMapper bean for JSON serialization.
     * Registers Java 8 time module for Instant, LocalDateTime, etc.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
    
    /**
     * Flyway bean to ensure migrations run before tests.
     * Migrations run immediately when bean is created.
     * Uses migrations from src/main/resources/db/migration.
     */
    @Bean
    @org.springframework.context.annotation.DependsOn("primaryDataSource")
    public org.flywaydb.core.Flyway flyway(@org.springframework.beans.factory.annotation.Qualifier("primaryDataSource") DataSource dataSource) {
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TestApplication.class);
        log.info("[TestApplication] Flyway bean creation started at {}", java.time.Instant.now());
        
        org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        
        log.info("[TestApplication] Starting Flyway migration at {}", java.time.Instant.now());
        flyway.migrate();
        log.info("[TestApplication] Flyway migration completed at {}", java.time.Instant.now());
        
        return flyway;
    }
    
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}

