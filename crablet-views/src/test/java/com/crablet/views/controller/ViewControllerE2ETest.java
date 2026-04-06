package com.crablet.views.controller;

import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventstore.EventStore;
import com.crablet.views.ViewSubscription;
import com.crablet.views.internal.ViewProcessorConfig;
import com.crablet.views.config.ViewsAutoConfiguration;
import com.crablet.views.integration.AbstractViewsTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.sql.DataSource;

/**
 * E2E tests for ViewController REST endpoints.
 * Tests all HTTP endpoints using WebTestClient with full Spring Boot context.
 */
@SpringBootTest(
    classes = ViewControllerE2ETest.TestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@DisplayName("ViewController E2E Tests")
class ViewControllerE2ETest extends AbstractViewsTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("viewsEventProcessor")
    private EventProcessor<ViewProcessorConfig, String> viewsEventProcessor;

    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void configureAdditionalProperties(DynamicPropertyRegistry registry) {
        registry.add("crablet.views.enabled", () -> "true");
    }

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbcTemplate);
        webTestClient = WebTestClient
            .bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
    }

    @Test
    @DisplayName("Given existing view, when getting status, then returns status with lag")
    void givenExistingView_whenGettingStatus_thenReturnsStatusWithLag() {
        // Given
        String viewName = "wallet-view";

        // When & Then
        webTestClient.get()
            .uri("/api/views/{viewName}/status", viewName)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("wallet-view")
            .jsonPath("$.status").exists()
            .jsonPath("$.lag").exists();
    }

    @Test
    @DisplayName("Given existing view, when pausing view, then returns success response")
    void givenExistingView_whenPausingView_thenReturnsSuccessResponse() {
        // Given
        String viewName = "wallet-view";

        // When & Then
        webTestClient.post()
            .uri("/api/views/{viewName}/pause", viewName)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("wallet-view")
            .jsonPath("$.status").isEqualTo("PAUSED")
            .jsonPath("$.message").isEqualTo("View projection paused successfully");
    }

    @Test
    @DisplayName("Given paused view, when resuming view, then returns success response")
    void givenPausedView_whenResumingView_thenReturnsSuccessResponse() {
        // Given
        String viewName = "wallet-view";
        // First pause the view
        webTestClient.post()
            .uri("/api/views/{viewName}/pause", viewName)
            .exchange()
            .expectStatus().isOk();

        // When & Then
        webTestClient.post()
            .uri("/api/views/{viewName}/resume", viewName)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("wallet-view")
            .jsonPath("$.status").isEqualTo("ACTIVE")
            .jsonPath("$.message").isEqualTo("View projection resumed successfully");
    }

    @Test
    @DisplayName("Given existing view, when resetting view, then returns success response")
    void givenExistingView_whenResettingView_thenReturnsSuccessResponse() {
        // Given
        String viewName = "wallet-view";

        // When & Then
        webTestClient.post()
            .uri("/api/views/{viewName}/reset", viewName)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("wallet-view")
            .jsonPath("$.status").isEqualTo("ACTIVE")
            .jsonPath("$.message").isEqualTo("View projection reset successfully");
    }

    @Test
    @DisplayName("Given existing view, when getting lag, then returns lag value")
    void givenExistingView_whenGettingLag_thenReturnsLagValue() {
        // Given
        String viewName = "wallet-view";

        // When & Then
        webTestClient.get()
            .uri("/api/views/{viewName}/lag", viewName)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("wallet-view")
            .jsonPath("$.lag").exists();
    }

    @Test
    @DisplayName("Given non-existent view, when pausing view, then returns 400")
    void givenNonExistentView_whenPausingView_thenReturns400() {
        // Given
        String viewName = "non-existent-view";

        // When & Then - pause returns false for non-existent views, causing 400
        webTestClient.post()
            .uri("/api/views/{viewName}/pause", viewName)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("non-existent-view")
            .jsonPath("$.error").isEqualTo("Failed to pause view projection");
    }

    @Test
    @DisplayName("Given non-existent view, when resuming view, then returns 400")
    void givenNonExistentView_whenResumingView_thenReturns400() {
        // Given
        String viewName = "non-existent-view";

        // When & Then - resume returns false for non-existent views, causing 400
        webTestClient.post()
            .uri("/api/views/{viewName}/resume", viewName)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("non-existent-view")
            .jsonPath("$.error").isEqualTo("Failed to resume view projection");
    }

    @Test
    @DisplayName("Given non-existent view, when resetting view, then returns 400")
    void givenNonExistentView_whenResettingView_thenReturns400() {
        // Given
        String viewName = "non-existent-view";

        // When & Then - reset returns false for non-existent views, causing 400
        webTestClient.post()
            .uri("/api/views/{viewName}/reset", viewName)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.viewName").isEqualTo("non-existent-view")
            .jsonPath("$.error").isEqualTo("Failed to reset view projection");
    }

    @org.springframework.boot.autoconfigure.SpringBootApplication
    @Configuration
    @Import(ViewsAutoConfiguration.class)
    static class TestConfig {
        @Bean
        public DataSource dataSource() {
            org.springframework.jdbc.datasource.SimpleDriverDataSource dataSource =
                    new org.springframework.jdbc.datasource.SimpleDriverDataSource();
            dataSource.setDriverClass(org.postgresql.Driver.class);
            dataSource.setUrl(AbstractViewsTest.postgres.getJdbcUrl());
            dataSource.setUsername(AbstractViewsTest.postgres.getUsername());
            dataSource.setPassword(AbstractViewsTest.postgres.getPassword());
            return dataSource;
        }

        @Bean
        @Primary
        public DataSource primaryDataSource(DataSource dataSource) {
            return dataSource;
        }

        @Bean
        @Qualifier("readDataSource")
        public DataSource readDataSource(DataSource dataSource) {
            return dataSource;
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public org.flywaydb.core.Flyway flyway(DataSource dataSource) {
            org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load();
            flyway.migrate();
            return flyway;
        }

        @Bean
        @org.springframework.context.annotation.DependsOn("flyway")
        public EventStore eventStore(
                DataSource dataSource,
                tools.jackson.databind.ObjectMapper objectMapper,
                com.crablet.eventstore.EventStoreConfig config,
                com.crablet.eventstore.ClockProvider clock,
                org.springframework.context.ApplicationEventPublisher eventPublisher) {
            return new com.crablet.eventstore.internal.EventStoreImpl(
                dataSource, dataSource, objectMapper, config, clock, eventPublisher);
        }

        @Bean
        public com.crablet.eventstore.EventStoreConfig eventStoreConfig() {
            return new com.crablet.eventstore.EventStoreConfig();
        }

        @Bean
        public com.crablet.eventstore.ClockProvider clockProvider() {
            return new com.crablet.eventstore.internal.ClockProviderImpl();
        }

        @Bean
        public tools.jackson.databind.ObjectMapper objectMapper() {
            tools.jackson.databind.ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder().disable(tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
            return mapper;
        }

        @Bean
        public com.crablet.eventpoller.InstanceIdProvider instanceIdProvider(Environment environment) {
            return new com.crablet.eventpoller.InstanceIdProvider(environment);
        }

        @Bean
        public TaskScheduler taskScheduler() {
            return new ThreadPoolTaskScheduler();
        }

        @Bean
        public org.springframework.context.ApplicationEventPublisher applicationEventPublisher() {
            return new org.springframework.context.support.GenericApplicationContext();
        }

        @Bean
        public com.crablet.views.config.ViewsConfig viewsConfig() {
            com.crablet.views.config.ViewsConfig config = new com.crablet.views.config.ViewsConfig();
            config.setEnabled(true);
            return config;
        }

        @Bean
        public ViewSubscription testViewSubscription() {
            return ViewSubscription.builder("wallet-view")
                    .eventTypes("WalletOpened", "DepositMade")
                    .requiredTags("wallet_id")
                    .build();
        }
    }
}
