package com.crablet.test.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared Flyway wiring for Spring test applications. Import this configuration from {@code TestApplication}
 * or nested {@code @Configuration} test classes that define a {@link DataSource}.
 */
@Configuration
public class CrabletFlywayConfiguration {

    @Bean
    public Flyway flyway(DataSource dataSource) {
        return CrabletFlywayMigration.migrate(dataSource);
    }
}
