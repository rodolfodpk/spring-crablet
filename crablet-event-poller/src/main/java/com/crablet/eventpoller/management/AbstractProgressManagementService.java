package com.crablet.eventpoller.management;

import com.crablet.eventpoller.progress.ProcessorStatus;
import com.crablet.eventstore.ClockProvider;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base class implementing {@link ProcessorManagementService}'s eight operations as pass-throughs
 * to a delegate. Subclasses add their own module-specific detail-query methods on top.
 */
public abstract class AbstractProgressManagementService<I> implements ProcessorManagementService<I> {

    protected final ProcessorManagementService<I> delegate;
    private final @Nullable DataSource dataSource;
    private final @Nullable ClockProvider clockProvider;

    protected AbstractProgressManagementService(ProcessorManagementService<I> delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
        this.dataSource = null;
        this.clockProvider = null;
    }

    protected AbstractProgressManagementService(
            ProcessorManagementService<I> delegate,
            @Nullable DataSource dataSource,
            @Nullable ClockProvider clockProvider) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (dataSource == null) throw new IllegalArgumentException("dataSource must not be null");
        if (clockProvider == null) throw new IllegalArgumentException("clockProvider must not be null");
        this.delegate = delegate;
        this.dataSource = dataSource;
        this.clockProvider = clockProvider;
    }

    @Override
    public boolean pause(I id) {
        return delegate.pause(id);
    }

    @Override
    public boolean resume(I id) {
        return delegate.resume(id);
    }

    @Override
    public boolean reset(I id) {
        return delegate.reset(id);
    }

    @Override
    public ProcessorStatus getStatus(I id) {
        return delegate.getStatus(id);
    }

    @Override
    public Map<I, ProcessorStatus> getAllStatuses() {
        return delegate.getAllStatuses();
    }

    @Override
    public @Nullable Long getLag(I id) {
        return delegate.getLag(id);
    }

    @Override
    public @Nullable BackoffInfo getBackoffInfo(I id) {
        return delegate.getBackoffInfo(id);
    }

    @Override
    public Map<I, BackoffInfo> getAllBackoffInfo() {
        return delegate.getAllBackoffInfo();
    }

    protected ClockProvider clockProvider() {
        if (clockProvider == null) {
            throw new IllegalStateException("No clockProvider configured");
        }
        return clockProvider;
    }

    protected <D> @Nullable D queryOne(
            String sql, StatementBinder binder, RowMapper<D> mapper, String description) {
        try (Connection connection = requiredDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapper.map(resultSet) : null;
            }
        } catch (SQLException e) {
            throw new ManagementQueryException("Failed to " + description, e);
        }
    }

    protected <D> List<D> queryAll(String sql, RowMapper<D> mapper, String description) {
        List<D> result = new ArrayList<>();
        try (Connection connection = requiredDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                result.add(mapper.map(resultSet));
            }
            return result;
        } catch (SQLException e) {
            throw new ManagementQueryException("Failed to " + description, e);
        }
    }

    private DataSource requiredDataSource() {
        if (dataSource == null) {
            throw new IllegalStateException("No dataSource configured");
        }
        return dataSource;
    }

    @FunctionalInterface
    protected interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    protected interface RowMapper<D> {
        D map(ResultSet resultSet) throws SQLException;
    }

    /** Uniform exception for module-specific progress detail queries. */
    public static class ManagementQueryException extends RuntimeException {
        public ManagementQueryException(String message, SQLException cause) {
            super(message, cause);
        }
    }
}
