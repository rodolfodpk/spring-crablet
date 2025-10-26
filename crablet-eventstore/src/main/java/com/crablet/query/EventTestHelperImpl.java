package com.crablet.query;

import com.crablet.store.Cursor;
import com.crablet.store.EventStoreConfig;
import com.crablet.store.EventStoreException;
import com.crablet.store.StoredEvent;
import com.crablet.store.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of EventTestHelper.
 * <p>
 * <strong>IMPORTANT: This helper is for testing purposes only.</strong>
 * Do not use in production code or command handlers.
 * <p>
 * Uses {@link QuerySqlBuilder} to generate SQL WHERE clauses dynamically.
 */
@Component
public class EventTestHelperImpl implements EventTestHelper {
    
    private final DataSource dataSource;
    private final QuerySqlBuilder sqlBuilder;
    private final EventStoreConfig config;
    
    /**
     * Singleton RowMapper for StoredEvent objects.
     * Reused across all queries to avoid lambda allocation overhead.
     */
    private final RowMapper<StoredEvent> EVENT_ROW_MAPPER = (rs, rowNum) -> {
        String type = rs.getString("type");
        String[] tagArray = (String[]) rs.getArray("tags").getArray();
        List<Tag> tags = parseTags(tagArray);
        byte[] data = rs.getString("data").getBytes(StandardCharsets.UTF_8);
        String transactionId = rs.getString("transaction_id");
        long position = rs.getLong("position");
        Instant occurredAt = rs.getTimestamp("occurred_at").toInstant();

        return new StoredEvent(type, tags, data, transactionId, position, occurredAt);
    };
    
    @Autowired
    public EventTestHelperImpl(DataSource dataSource, EventStoreConfig config) {
        this.dataSource = dataSource;
        this.config = config;
        this.sqlBuilder = new QuerySqlBuilderImpl();
    }
    
    @Override
    public List<StoredEvent> query(Query query, Cursor after) {
        try {
            // Build SQL query directly instead of using the function
            StringBuilder sql = new StringBuilder("SELECT type, tags, data, transaction_id, position, occurred_at FROM events");
            List<Object> params = new ArrayList<>();

            // Use shared WHERE clause builder
            String whereClause = sqlBuilder.buildWhereClause(query, after, params);
            if (!whereClause.isEmpty()) {
                sql.append(" WHERE ").append(whereClause);
            }

            sql.append(" ORDER BY transaction_id, position ASC");

            // Use raw JDBC with server-side cursor
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false); // Required for server-side cursor
                
                try (PreparedStatement stmt = connection.prepareStatement(
                        sql.toString(),
                        ResultSet.TYPE_FORWARD_ONLY,
                        ResultSet.CONCUR_READ_ONLY)) {
                    
                    stmt.setFetchSize(config.getFetchSize()); // Enables server-side cursor

                    // Set parameters
                    for (int i = 0; i < params.size(); i++) {
                        Object param = params.get(i);
                        if (param instanceof String[]) {
                            stmt.setArray(i + 1, connection.createArrayOf("text", (String[]) param));
                        } else {
                            stmt.setObject(i + 1, param);
                        }
                    }

                    // Execute and collect results
                    List<StoredEvent> events = new ArrayList<>();
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            events.add(EVENT_ROW_MAPPER.mapRow(rs, rs.getRow()));
                        }
                    }
                    
                    connection.commit();
                    return events;
                }
            }
        } catch (Exception e) {
            throw new EventStoreException("Failed to query events", e);
        }
    }
    
    private List<Tag> parseTags(String[] tagArray) {
        List<Tag> tags = new ArrayList<>(tagArray.length);  // Pre-sized to avoid resizing
        for (String tagStr : tagArray) {
            int eqIndex = tagStr.indexOf('=');  // Faster than split()
            if (eqIndex > 0) {
                tags.add(new Tag(
                        tagStr.substring(0, eqIndex),
                        tagStr.substring(eqIndex + 1)
                ));
            } else {
                tags.add(new Tag(tagStr, ""));
            }
        }
        return tags;
    }
}

