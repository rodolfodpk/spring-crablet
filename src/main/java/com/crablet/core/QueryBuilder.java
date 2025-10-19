package com.crablet.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent builder for constructing DCB queries.
 * Business-agnostic, reusable across domains.
 */
public class QueryBuilder {
    private final List<QueryItem> items = new ArrayList<>();
    
    private QueryBuilder() {}
    
    public static QueryBuilder create() {
        return new QueryBuilder();
    }
    
    /**
     * Add a query item matching specific event types and tags.
     */
    public QueryBuilder matching(String[] eventTypes, Tag... tags) {
        items.add(QueryItem.of(Arrays.asList(eventTypes), Arrays.asList(tags)));
        return this;
    }
    
    /**
     * Add multiple query items at once.
     */
    public QueryBuilder matching(List<QueryItem> queryItems) {
        items.addAll(queryItems);
        return this;
    }
    
    /**
     * Build the final Query.
     */
    public Query build() {
        return Query.of(new ArrayList<>(items));
    }
    
    /**
     * Convert to AppendConditionBuilder for creating conditions.
     */
    public AppendConditionBuilder toAppendCondition(Cursor cursor) {
        return new AppendConditionBuilder(this.build(), cursor);
    }
}

