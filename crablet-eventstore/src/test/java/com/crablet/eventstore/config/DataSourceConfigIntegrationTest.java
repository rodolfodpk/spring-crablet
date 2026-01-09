package com.crablet.eventstore.config;

import com.crablet.eventstore.integration.AbstractCrabletTest;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DataSourceConfig bean creation.
 * Tests Spring context configuration, conditional bean creation, and real database connections.
 */
@SpringBootTest(classes = {com.crablet.eventstore.integration.TestApplication.class})
@Import(DataSourceConfig.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("DataSourceConfig Integration Tests")
class DataSourceConfigIntegrationTest extends AbstractCrabletTest {

    @Autowired
    @Qualifier("primaryDataSource")
    protected DataSource primaryDataSource;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ApplicationContext applicationContext;

    @Test
    @DisplayName("Should create primaryDataSource bean and mark as @Primary")
    void shouldCreatePrimaryDataSourceBean_WithSpringContext() {
        // Given & When - Bean is autowired

        // Then
        assertThat(primaryDataSource).isNotNull();
        assertThat(primaryDataSource).isInstanceOf(HikariDataSource.class);
    }

    @Test
    @DisplayName("Should create jdbcTemplate bean using primaryDataSource")
    void shouldCreateJdbcTemplateBean_WithPrimaryDataSource() {
        // Given & When - jdbcTemplate is autowired

        // Then
        assertThat(jdbcTemplate).isNotNull();
        assertThat(jdbcTemplate.getDataSource()).isSameAs(primaryDataSource);
    }

    @Test
    @DisplayName("Should create readDataSource bean returning primaryDataSource when property missing (default)")
    void shouldCreateReadDataSourceFallback_WhenPropertyMissing() {
        // Given - No property set (default behavior, matchIfMissing=true)

        // When
        DataSource readDataSource = applicationContext.getBean("readDataSource", DataSource.class);

        // Then - Should return same instance as primaryDataSource (fallback)
        assertThat(readDataSource).isNotNull();
        assertThat(readDataSource).isSameAs(primaryDataSource);
    }
}

@SpringBootTest(classes = {com.crablet.eventstore.integration.TestApplication.class})
@Import(DataSourceConfig.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "crablet.eventstore.read-replicas.enabled=false")
@DisplayName("DataSourceConfig Integration Tests - Replicas Disabled")
class DataSourceConfigIntegrationTest_ReplicasDisabled extends AbstractCrabletTest {

    @Autowired
    @Qualifier("primaryDataSource")
    private DataSource primaryDataSource;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Should create readDataSource bean returning primaryDataSource when replicas disabled")
    void shouldCreateReadDataSourceBean_ReturningPrimaryDataSource() {
        // Given & When
        DataSource readDataSource = applicationContext.getBean("readDataSource", DataSource.class);

        // Then - Should return same instance as primaryDataSource
        assertThat(readDataSource).isNotNull();
        assertThat(readDataSource).isSameAs(primaryDataSource);
    }
}

@SpringBootTest(classes = {com.crablet.eventstore.integration.TestApplication.class})
@Import(DataSourceConfig.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
    "crablet.eventstore.read-replicas.enabled=true",
    "crablet.eventstore.read-replicas.url=${spring.datasource.url}" // Use same DB for testing
})
@DisplayName("DataSourceConfig Integration Tests - Replicas Enabled")
class DataSourceConfigIntegrationTest_ReplicasEnabled extends AbstractCrabletTest {

    @Autowired
    @Qualifier("primaryDataSource")
    private DataSource primaryDataSource;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Should create readDataSource bean with separate replica instance")
    void shouldCreateReadDataSourceBean_WithSeparateReplicaInstance() {
        // Given & When
        DataSource readDataSource = applicationContext.getBean("readDataSource", DataSource.class);

        // Then - Should be a separate HikariDataSource instance (even if pointing to same DB)
        assertThat(readDataSource).isNotNull();
        assertThat(readDataSource).isInstanceOf(HikariDataSource.class);
        // Note: In a real scenario with actual replica, this would be a different instance
        // For testing, we use same URL but verify it's a separate DataSource instance
        assertThat(readDataSource).isNotSameAs(primaryDataSource);
    }

    @Test
    @DisplayName("Should use primary credentials when replica credentials not set")
    void shouldUsePrimaryCredentials_WhenReplicaCredentialsNotSet() {
        // Given - Replica enabled but no username/password in properties
        HikariDataSource primaryHikari = (HikariDataSource) primaryDataSource;
        String primaryUsername = primaryHikari.getUsername();
        String primaryPassword = primaryHikari.getPassword();

        // When
        HikariDataSource readHikari = (HikariDataSource) applicationContext.getBean("readDataSource", DataSource.class);

        // Then - Should fallback to primary credentials
        assertThat(readHikari.getUsername()).isEqualTo(primaryUsername);
        assertThat(readHikari.getPassword()).isEqualTo(primaryPassword);
    }
}

@SpringBootTest(classes = {com.crablet.eventstore.integration.TestApplication.class})
@Import(DataSourceConfig.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
    "crablet.eventstore.read-replicas.enabled=true",
    "crablet.eventstore.read-replicas.url=${spring.datasource.url}",
    "crablet.eventstore.read-replicas.hikari.username=replica_user",
    "crablet.eventstore.read-replicas.hikari.password=replica_pass"
})
@DisplayName("DataSourceConfig Integration Tests - Replicas Enabled With Custom Credentials")
class DataSourceConfigIntegrationTest_ReplicasEnabledWithCustomCredentials extends AbstractCrabletTest {

    static {
        // Create replica_user in test database before Spring context initializes
        // This must be done in static block to run before Spring Boot starts
        var postgres = AbstractCrabletTest.getPostgresContainer();
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword())) {
            try (Statement stmt = conn.createStatement()) {
                // Drop user if exists (ignore errors)
                try {
                    stmt.execute("DROP USER IF EXISTS replica_user");
                } catch (Exception e) {
                    // Ignore if user doesn't exist
                }
                // Create user with password
                stmt.execute("CREATE USER replica_user WITH PASSWORD 'replica_pass'");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create replica_user for test", e);
        }
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Should use replica credentials when provided")
    void shouldUseReplicaCredentials_WhenReplicaCredentialsSet() {
        // Given & When
        HikariDataSource readHikari = (HikariDataSource) applicationContext.getBean("readDataSource", DataSource.class);

        // Then - Should use replica credentials
        assertThat(readHikari.getUsername()).isEqualTo("replica_user");
        assertThat(readHikari.getPassword()).isEqualTo("replica_pass");
    }
}

@SpringBootTest(classes = {com.crablet.eventstore.integration.TestApplication.class})
@Import(DataSourceConfig.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
    "crablet.eventstore.read-replicas.enabled=true",
    "crablet.eventstore.read-replicas.url=${spring.datasource.url}",
    "crablet.eventstore.read-replicas.hikari.maximum-pool-size=75",
    "crablet.eventstore.read-replicas.hikari.minimum-idle=15"
})
@DisplayName("DataSourceConfig Integration Tests - Replicas Enabled With Custom Pool Properties")
class DataSourceConfigIntegrationTest_ReplicasEnabledWithCustomPoolProperties extends AbstractCrabletTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Should configure HikariCP pool properties from ReadReplicaProperties")
    void shouldConfigureHikariPoolProperties_FromReadReplicaProperties() {
        // Given & When
        HikariDataSource readHikari = (HikariDataSource) applicationContext.getBean("readDataSource", DataSource.class);

        // Then - Should use configured pool sizes
        assertThat(readHikari.getMaximumPoolSize()).isEqualTo(75);
        assertThat(readHikari.getMinimumIdle()).isEqualTo(15);
    }
}
