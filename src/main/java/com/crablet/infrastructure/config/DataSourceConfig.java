package com.crablet.infrastructure.config;

import com.crablet.infrastructure.datasource.LoadBalancedDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for DataSource beans with optional read replica support.
 * <p>
 * Provides three DataSource beans:
 * <ul>
 *   <li><strong>primaryDataSource</strong>: Main database for writes and fallback reads (marked @Primary)</li>
 *   <li><strong>readDataSource</strong>: Load-balanced read replicas (conditional, falls back to primary if disabled)</li>
 *   <li><strong>jdbcTemplate</strong>: Uses primaryDataSource for outbox and other write operations</li>
 * </ul>
 * <p>
 * When read replicas are disabled (default), readDataSource returns the same instance as primaryDataSource,
 * ensuring seamless backward compatibility.
 */
@Configuration
@EnableConfigurationProperties(ReadReplicaProperties.class)
public class DataSourceConfig {
    
    /**
     * Primary DataSource for write operations and as fallback for reads.
     * <p>
     * This delegates to Spring Boot's auto-configured DataSource but registers it
     * under the "primaryDataSource" name for explicit injection.
     */
    @Bean(name = "primaryDataSource")
    @Primary
    public DataSource primaryDataSource(DataSourceProperties properties) {
        return DataSourceBuilder.create()
            .type(HikariDataSource.class)
            .url(properties.getUrl())
            .username(properties.getUsername())
            .password(properties.getPassword())
            .driverClassName(properties.getDriverClassName())
            .build();
    }
    
    /**
     * Read DataSource for read operations, with optional load balancing across replicas.
     * <p>
     * <strong>When replicas enabled:</strong> Returns a LoadBalancedDataSource that distributes
     * reads across multiple replica databases using round-robin.
     * <p>
     * <strong>When replicas disabled:</strong> Returns the primaryDataSource for seamless fallback.
     * <p>
     * <strong>Usage:</strong> Inject with @Qualifier("readDataSource") for read operations.
     */
    @Bean(name = "readDataSource")
    @ConditionalOnProperty(
        name = "crablet.eventstore.read-replicas.enabled",
        havingValue = "true",
        matchIfMissing = false
    )
    public DataSource readDataSourceWithReplicas(
            @Qualifier("primaryDataSource") DataSource primaryDataSource,
            ReadReplicaProperties readReplicaProperties) {
        
        List<String> replicaUrls = readReplicaProperties.getUrls();
        if (replicaUrls == null || replicaUrls.isEmpty()) {
            throw new IllegalStateException(
                "crablet.eventstore.read-replicas.enabled=true but no replica URLs configured");
        }
        
        // Get primary datasource properties for replica configuration
        HikariDataSource primaryHikari = (HikariDataSource) primaryDataSource;
        
        // Create DataSource for each replica URL
        List<DataSource> replicaDataSources = new ArrayList<>();
        for (String url : replicaUrls) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            // Use primary datasource credentials (or override from properties if specified)
            config.setUsername(
                readReplicaProperties.getHikari().getUsername() != null 
                    ? readReplicaProperties.getHikari().getUsername() 
                    : primaryHikari.getUsername()
            );
            config.setPassword(
                readReplicaProperties.getHikari().getPassword() != null 
                    ? readReplicaProperties.getHikari().getPassword() 
                    : primaryHikari.getPassword()
            );
            config.setMaximumPoolSize(readReplicaProperties.getHikari().getMaximumPoolSize());
            config.setMinimumIdle(readReplicaProperties.getHikari().getMinimumIdle());
            replicaDataSources.add(new HikariDataSource(config));
        }
        
        return new LoadBalancedDataSource(
            replicaDataSources,
            primaryDataSource,
            readReplicaProperties.isFallbackToPrimary()
        );
    }
    
    /**
     * Read DataSource fallback when replicas are disabled.
     * <p>
     * Returns the primaryDataSource to ensure all components always have a valid readDataSource bean.
     */
    @Bean(name = "readDataSource")
    @ConditionalOnProperty(
        name = "crablet.eventstore.read-replicas.enabled",
        havingValue = "false",
        matchIfMissing = true
    )
    public DataSource readDataSourceWithoutReplicas(
            @Qualifier("primaryDataSource") DataSource primaryDataSource) {
        // When replicas disabled, return primary for seamless fallback
        return primaryDataSource;
    }
    
    /**
     * JdbcTemplate bean using the primary DataSource.
     * <p>
     * Used by OutboxProcessor and OutboxLeaderElector for write operations and position tracking.
     */
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}

