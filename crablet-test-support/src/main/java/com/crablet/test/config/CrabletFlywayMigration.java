package com.crablet.test.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/**
 * Runs classpath Flyway migrations used by Crablet integration tests ({@code classpath:db/migration}).
 */
public final class CrabletFlywayMigration {

    public static final String CLASSPATH_LOCATIONS = "classpath:db/migration";

    private CrabletFlywayMigration() {}

    public static Flyway migrate(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(CLASSPATH_LOCATIONS)
                .load();
        flyway.migrate();
        return flyway;
    }
}
