package com.crablet.eventstore.config;

import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.EventStoreConfig;
import com.crablet.eventstore.ReadDataSource;
import com.crablet.eventstore.WriteDataSource;
import com.crablet.eventstore.internal.ClockProviderImpl;
import com.crablet.eventstore.internal.EventStoreNotificationProperties;
import com.crablet.eventstore.internal.EventRepositoryImpl;
import com.crablet.eventstore.internal.EventStoreImpl;
import com.crablet.eventstore.internal.ReadReplicaProperties;
import com.crablet.eventstore.notify.EventAppendNotifier;
import com.crablet.eventstore.notify.PostgresNotifyEventAppendNotifier;
import com.crablet.eventstore.query.EventRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Auto-configuration for the Crablet EventStore.
 * <p>
 * Activated automatically when the module is on the classpath.
 * Every bean uses {@code @ConditionalOnMissingBean}, so you can override any of them.
 * <p>
 * Provides the following beans when not already declared by the application:
 * <ul>
 *   <li>{@link WriteDataSource} — wraps Spring Boot's main datasource</li>
 *   <li>{@link ReadDataSource} — same datasource by default; can target a read replica</li>
 *   <li>{@link ClockProvider} — system UTC clock</li>
 *   <li>{@link EventStore} — core event sourcing API</li>
 *   <li>{@link EventRepository} — low-level event query API</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties({ReadReplicaProperties.class, EventStoreNotificationProperties.class})
public class EventStoreAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "crablet.eventstore")
    @ConditionalOnMissingBean(EventStoreConfig.class)
    public EventStoreConfig eventStoreConfig() {
        return new EventStoreConfig();
    }

    @Bean
    @ConfigurationProperties("spring.datasource")
    @ConditionalOnMissingBean(DataSourceProperties.class)
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "dataSource")
    @ConfigurationProperties("spring.datasource.hikari")
    @ConditionalOnMissingBean(name = "dataSource")
    public DataSource dataSource(DataSourceProperties dataSourceProperties) {
        return dataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public WriteDataSource writeDataSource(@Qualifier("dataSource") DataSource dataSource) {
        return new WriteDataSource(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReadDataSource readDataSource(
            WriteDataSource writeDataSource,
            ReadReplicaProperties readReplicaProperties,
            DataSourceProperties dataSourceProperties) {
        if (!readReplicaProperties.isEnabled()) {
            return new ReadDataSource(writeDataSource.dataSource());
        }

        String replicaUrl = readReplicaProperties.getUrl();
        if (replicaUrl == null || replicaUrl.trim().isEmpty()) {
            throw new IllegalStateException(
                    "crablet.eventstore.read-replicas.enabled=true but no replica URL configured");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(replicaUrl);

        String username = readReplicaProperties.getHikari().getUsername();
        if (username == null) {
            username = dataSourceProperties.getUsername();
        }
        if (username != null) {
            config.setUsername(username);
        }

        String password = readReplicaProperties.getHikari().getPassword();
        if (password == null) {
            password = dataSourceProperties.getPassword();
        }
        if (password != null) {
            config.setPassword(password);
        }

        String driverClassName = dataSourceProperties.getDriverClassName();
        if (driverClassName != null) {
            config.setDriverClassName(driverClassName);
        }

        config.setMaximumPoolSize(readReplicaProperties.getHikari().getMaximumPoolSize());
        config.setMinimumIdle(readReplicaProperties.getHikari().getMinimumIdle());

        return new ReadDataSource(new HikariDataSource(config));
    }

    @Bean(name = "primaryDataSource")
    @ConditionalOnMissingBean(name = "primaryDataSource")
    public DataSource primaryDataSource(WriteDataSource writeDataSource) {
        return writeDataSource.dataSource();
    }

    @Bean(name = "readDataSource")
    @ConditionalOnMissingBean(name = "readDataSource")
    public DataSource legacyReadDataSource(ReadDataSource readDataSource) {
        return readDataSource.dataSource();
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcTemplate jdbcTemplate(WriteDataSource writeDataSource) {
        return new JdbcTemplate(writeDataSource.dataSource());
    }

    @Bean
    @ConditionalOnMissingBean
    public ClockProvider clockProvider() {
        return new ClockProviderImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public EventAppendNotifier eventAppendNotifier(
            WriteDataSource writeDataSource,
            EventStoreNotificationProperties notificationProperties) {
        return new PostgresNotifyEventAppendNotifier(
                writeDataSource.dataSource(),
                notificationProperties.getChannel(),
                notificationProperties.getPayload());
    }

    @Bean
    @ConditionalOnMissingBean
    public EventStore eventStore(
            WriteDataSource writeDataSource,
            ReadDataSource readDataSource,
            ObjectMapper objectMapper,
            EventStoreConfig config,
            ClockProvider clock,
            ApplicationEventPublisher eventPublisher,
            EventAppendNotifier eventAppendNotifier) {
        return new EventStoreImpl(
                writeDataSource.dataSource(),
                readDataSource.dataSource(),
                objectMapper,
                config,
                clock,
                eventPublisher,
                eventAppendNotifier);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventRepository eventRepository(
            WriteDataSource writeDataSource,
            EventStoreConfig config) {
        return new EventRepositoryImpl(writeDataSource.dataSource(), config);
    }
}
