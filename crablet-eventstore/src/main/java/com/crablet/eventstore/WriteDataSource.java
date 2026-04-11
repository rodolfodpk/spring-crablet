package com.crablet.eventstore;

import javax.sql.DataSource;

/**
 * Typed wrapper for the write datasource.
 * Framework components that append events, write projections, or acquire locks
 * should depend on this type instead of a named {@link DataSource}.
 */
public record WriteDataSource(DataSource dataSource) {

    public WriteDataSource {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must not be null");
        }
    }
}
