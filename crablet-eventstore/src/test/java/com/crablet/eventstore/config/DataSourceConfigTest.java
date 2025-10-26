package com.crablet.eventstore.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for DataSourceConfig bean creation.
 * Note: Tests that don't require actual database connections only.
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
}
