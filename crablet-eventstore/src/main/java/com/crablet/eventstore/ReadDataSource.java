package com.crablet.eventstore;

import javax.sql.DataSource;

/**
 * Typed wrapper for the read datasource.
 * Framework components that fetch events or query processor status should depend
 * on this type instead of a named {@link DataSource}.
 */
public record ReadDataSource(DataSource dataSource) {

    public ReadDataSource {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must not be null");
        }
    }
}
