package com.crablet.eventstore.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DataSourceConfig bean creation.
 * Tests that don't require actual database connections.
 * 
 * Note: Tests requiring database connections are in DataSourceConfigIntegrationTest.
 */
class DataSourceConfigTest {
    
    @Test
    void testReadDataSourceWithoutReplicas() {
        DataSourceConfig config = new DataSourceConfig();
        
        // Create a simple mock datasource
        DataSource primaryDataSource = new HikariDataSource();
        
        // Test fallback to primary when replicas disabled
        DataSource readDataSource = config.readDataSourceWithoutReplicas(primaryDataSource);
        
        assertNotNull(readDataSource);
        assertSame(primaryDataSource, readDataSource, "Should return same instance when replicas disabled");
    }
    
    @Test
    void testReadDataSourceWithReplicasWithoutUrl() {
        DataSourceConfig config = new DataSourceConfig();
        DataSource primaryDataSource = new HikariDataSource();
        
        ReadReplicaProperties replicaProps = new ReadReplicaProperties();
        replicaProps.setEnabled(true);
        // URL is null by default
        
        // Should throw exception if no URL configured
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> config.readDataSourceWithReplicas(primaryDataSource, replicaProps)
        );
        
        assertTrue(exception.getMessage().contains("no replica URL configured") 
                || exception.getMessage().contains("enabled=true but no replica URL configured"));
    }
    
    @Test
    void testReadDataSourceWithReplicasWithEmptyUrl() {
        DataSourceConfig config = new DataSourceConfig();
        DataSource primaryDataSource = new HikariDataSource();
        
        ReadReplicaProperties replicaProps = new ReadReplicaProperties();
        replicaProps.setEnabled(true);
        replicaProps.setUrl(""); // Empty URL
        
        // Should throw exception if empty URL
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> config.readDataSourceWithReplicas(primaryDataSource, replicaProps)
        );
        
        assertTrue(exception.getMessage().contains("no replica URL configured") 
                || exception.getMessage().contains("enabled=true but no replica URL configured"));
    }
    
    @Test
    void testJdbcTemplateCreation() {
        DataSourceConfig config = new DataSourceConfig();
        DataSource dataSource = new HikariDataSource();
        
        JdbcTemplate jdbcTemplate = config.jdbcTemplate(dataSource);
        
        assertNotNull(jdbcTemplate);
        assertSame(dataSource, jdbcTemplate.getDataSource());
    }
    
    @Test
    void testReplicaPropertiesWithHikariConfiguration() {
        ReadReplicaProperties properties = new ReadReplicaProperties();
        properties.setUrl("jdbc:postgresql://replica:5432/db");
        
        ReadReplicaProperties.HikariProperties hikari = properties.getHikari();
        hikari.setMaximumPoolSize(75);
        hikari.setMinimumIdle(15);
        
        assertEquals(75, hikari.getMaximumPoolSize());
        assertEquals(15, hikari.getMinimumIdle());
    }

    @Test
    @DisplayName("readDataSourceWithReplicas should throw ClassCastException when primaryDataSource is not HikariDataSource")
    void readDataSourceWithReplicas_WhenPrimaryDataSourceNotHikari_ShouldThrowClassCastException() {
        // Given
        DataSourceConfig config = new DataSourceConfig();
        DataSource primaryDataSource = new org.springframework.jdbc.datasource.SimpleDriverDataSource();
        // SimpleDriverDataSource is not a HikariDataSource

        ReadReplicaProperties replicaProps = new ReadReplicaProperties();
        replicaProps.setUrl("jdbc:postgresql://replica:5432/db");

        // When & Then - Should throw ClassCastException when casting to HikariDataSource
        assertThrows(ClassCastException.class, () ->
                config.readDataSourceWithReplicas(primaryDataSource, replicaProps)
        );
    }
}
