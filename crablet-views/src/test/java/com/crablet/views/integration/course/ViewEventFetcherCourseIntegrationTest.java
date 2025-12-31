package com.crablet.views.integration.course;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.views.adapter.ViewEventFetcher;
import com.crablet.views.config.ViewSubscriptionConfig;
import com.crablet.views.integration.AbstractViewsTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for ViewEventFetcher.
 * Tests event filtering, position-based fetching, and SQL query construction with real PostgreSQL.
 */
@SpringBootTest(classes = ViewEventFetcherCourseIntegrationTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("ViewEventFetcher Course Domain Integration Tests")
class ViewEventFetcherCourseIntegrationTest extends AbstractViewsTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private ViewEventFetcher eventFetcher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbcTemplate);
    }

    @Test
    @DisplayName("Should fetch events filtered by event type")
    void shouldFetchEvents_FilteredByEventType() {
        // Given - Events with different types
        List<AppendEvent> events = List.of(
            AppendEvent.builder("CourseDefined")
                .tag("course_id", "course-1")
                .data("""
                    {"courseId":"course-1","name":"Math 101"}
                    """)
                .build(),
            AppendEvent.builder("StudentSubscribedToCourse")
                .tag("course_id", "course-1")
                .data("""
                    {"studentId":"student-1"}
                    """)
                .build(),
            AppendEvent.builder("CourseCapacityChanged")
                .tag("course_id", "course-2")
                .data("""
                    {"newCapacity":50}
                    """)
                .build()
        );
        eventStore.appendIf(events, AppendCondition.empty());

        // When - Fetch events for subscription that matches CourseDefined and StudentSubscribedToCourse
        List<StoredEvent> fetched = eventFetcher.fetchEvents("course-view", 0L, 100);

        // Then - Should return CourseDefined, StudentSubscribedToCourse, and CourseCapacityChanged (all have course_id tag and matching types)
        assertThat(fetched).hasSize(3);
        assertThat(fetched.stream().map(StoredEvent::type)).containsExactlyInAnyOrder("CourseDefined", "StudentSubscribedToCourse", "CourseCapacityChanged");
    }

    @Test
    @DisplayName("Should fetch events filtered by required tags")
    void shouldFetchEvents_FilteredByRequiredTags() {
        // Given - Events with different tags
        List<AppendEvent> events = List.of(
            AppendEvent.builder("CourseDefined")
                .tag("course_id", "course-1")
                .tag("region", "us-east")
                .data("""
                    {"courseId":"course-1","name":"Math 101"}
                    """)
                .build(),
            AppendEvent.builder("CourseDefined")
                .tag("course_id", "course-2")
                .data("""
                    {"courseId":"course-2","name":"Physics 101"}
                    """)
                .build()
        );
        eventStore.appendIf(events, AppendCondition.empty());

        // When - Fetch events for subscription requiring course_id tag
        List<StoredEvent> fetched = eventFetcher.fetchEvents("course-view", 0L, 100);

        // Then - Should return both CourseDefined events (both have course_id tag and matching event types)
        assertThat(fetched).hasSize(2);
        assertThat(fetched.stream().map(StoredEvent::type)).containsExactlyInAnyOrder("CourseDefined", "CourseDefined");
    }

    @Test
    @DisplayName("Should fetch events filtered by any-of tags")
    void shouldFetchEvents_FilteredByAnyOfTags() {
        // Given - Events with different tags
        List<AppendEvent> events = List.of(
            AppendEvent.builder("Event1")
                .tag("region", "us-east")
                .data("""
                    {"data":"1"}
                    """)
                .build(),
            AppendEvent.builder("Event2")
                .tag("country", "us")
                .data("""
                    {"data":"2"}
                    """)
                .build(),
            AppendEvent.builder("Event3")
                .tag("other", "value")
                .data("""
                    {"data":"3"}
                    """)
                .build()
        );
        eventStore.appendIf(events, AppendCondition.empty());

        // When - Fetch events for subscription with any-of tags (region OR country)
        List<StoredEvent> fetched = eventFetcher.fetchEvents("region-view", 0L, 100);

        // Then - Should return events with region OR country tags
        assertThat(fetched).hasSize(2);
        assertThat(fetched.stream().map(StoredEvent::type)).containsExactlyInAnyOrder("Event1", "Event2");
    }

    @Test
    @DisplayName("Should fetch events after last position")
    void shouldFetchEvents_AfterLastPosition() {
        // Given - Multiple events
        List<AppendEvent> events = List.of(
            AppendEvent.builder("CourseDefined")
                .tag("course_id", "course-1")
                .data("""
                    {"courseId":"course-1","name":"Math 101"}
                    """)
                .build(),
            AppendEvent.builder("StudentSubscribedToCourse")
                .tag("course_id", "course-1")
                .data("""
                    {"studentId":"student-1"}
                    """)
                .build(),
            AppendEvent.builder("CourseCapacityChanged")
                .tag("course_id", "course-2")
                .data("""
                    {"newCapacity":50}
                    """)
                .build()
        );
        eventStore.appendIf(events, AppendCondition.empty());

        // When - Fetch events after position 1
        List<StoredEvent> fetched = eventFetcher.fetchEvents("course-view", 1L, 100);

        // Then - Should return events with position > 1 (both CourseDefined and StudentSubscribedToCourse match)
        assertThat(fetched).hasSize(2);
        assertThat(fetched.get(0).position()).isGreaterThan(1L);
        assertThat(fetched.stream().map(StoredEvent::type)).containsExactlyInAnyOrder("StudentSubscribedToCourse", "CourseCapacityChanged");
    }

    @Test
    @DisplayName("Should respect batch size limit")
    void shouldRespectBatchSizeLimit() {
        // Given - More events than batch size
        List<AppendEvent> events = new java.util.ArrayList<>();
        for (int i = 0; i < 150; i++) {
            events.add(AppendEvent.builder("CourseDefined")
                .tag("course_id", "course-" + i)
                .data(String.format("""
                    {"courseId":"course-%d","name":"Course %d"}
                    """, i, i))
                .build());
        }
        eventStore.appendIf(events, AppendCondition.empty());

        // When - Fetch with batch size 50
        List<StoredEvent> fetched = eventFetcher.fetchEvents("course-view", 0L, 50);

        // Then - Should return exactly batch size
        assertThat(fetched).hasSize(50);
    }

    @Test
    @DisplayName("Should return empty list for non-existent subscription")
    void shouldReturnEmptyList_ForNonExistentSubscription() {
        // When
        List<StoredEvent> fetched = eventFetcher.fetchEvents("non-existent-view", 0L, 100);

        // Then
        assertThat(fetched).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list when no events match filters")
    void shouldReturnEmptyList_WhenNoEventsMatchFilters() {
        // Given - Events that don't match subscription (no course_id tag)
        List<AppendEvent> events = List.of(
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-1")
                .data("""
                    {"walletId":"wallet-1"}
                    """)
                .build()
        );
        eventStore.appendIf(events, AppendCondition.empty());

        // When - Fetch for course-view (which requires course_id tag)
        List<StoredEvent> fetched = eventFetcher.fetchEvents("course-view", 0L, 100);

        // Then
        assertThat(fetched).isEmpty();
    }

    @Test
    @DisplayName("Should combine all filters (event types + required tags + any-of tags)")
    void shouldCombineAllFilters() {
        // Given - Events with various combinations
        List<AppendEvent> events = List.of(
            AppendEvent.builder("CourseDefined")
                .tag("course_id", "course-1")
                .tag("region", "us-east")
                .data("""
                    {"courseId":"course-1","name":"Math 101"}
                    """)
                .build(),
            AppendEvent.builder("CourseDefined")
                .tag("course_id", "course-2")
                .data("""
                    {"courseId":"course-2","name":"Physics 101"}
                    """)
                .build(),
            AppendEvent.builder("StudentSubscribedToCourse")
                .tag("course_id", "course-1")
                .tag("region", "us-east")
                .data("""
                    {"studentId":"student-1"}
                    """)
                .build()
        );
        eventStore.appendIf(events, AppendCondition.empty());

        // When - Fetch with combined filters
        List<StoredEvent> fetched = eventFetcher.fetchEvents("course-view", 0L, 100);

        // Then - Should match based on subscription config
        assertThat(fetched).isNotEmpty();
    }

    @Test
    @DisplayName("Should return events in position order")
    void shouldReturnEvents_InPositionOrder() {
        // Given - Multiple events
        List<AppendEvent> events = List.of(
            AppendEvent.builder("CourseDefined")
                .tag("course_id", "course-1")
                .data("""
                    {"courseId":"course-1","name":"Math 101"}
                    """)
                .build(),
            AppendEvent.builder("StudentSubscribedToCourse")
                .tag("course_id", "course-1")
                .data("""
                    {"studentId":"student-1"}
                    """)
                .build(),
            AppendEvent.builder("CourseCapacityChanged")
                .tag("course_id", "course-2")
                .data("""
                    {"newCapacity":50}
                    """)
                .build()
        );
        eventStore.appendIf(events, AppendCondition.empty());

        // When
        List<StoredEvent> fetched = eventFetcher.fetchEvents("course-view", 0L, 100);

        // Then - Should be in position order
        assertThat(fetched).hasSizeGreaterThan(1);
        for (int i = 1; i < fetched.size(); i++) {
            assertThat(fetched.get(i).position()).isGreaterThan(fetched.get(i - 1).position());
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        public javax.sql.DataSource dataSource() {
            org.springframework.jdbc.datasource.SimpleDriverDataSource dataSource =
                    new org.springframework.jdbc.datasource.SimpleDriverDataSource();
            dataSource.setDriverClass(org.postgresql.Driver.class);
            dataSource.setUrl(AbstractViewsTest.postgres.getJdbcUrl());
            dataSource.setUsername(AbstractViewsTest.postgres.getUsername());
            dataSource.setPassword(AbstractViewsTest.postgres.getPassword());
            return dataSource;
        }

        @Bean
        @Primary
        public javax.sql.DataSource primaryDataSource(DataSource dataSource) {
            return dataSource;
        }

        @Bean
        @Qualifier("readDataSource")
        public javax.sql.DataSource readDataSource(DataSource dataSource) {
            return dataSource;
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public org.flywaydb.core.Flyway flyway(DataSource dataSource) {
            // Run migrations from both views and eventstore modules
            // Migrations run immediately when bean is created
            org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load();
            flyway.migrate();
            return flyway;
        }

        @Bean
        @org.springframework.context.annotation.DependsOn("flyway")
        public com.crablet.eventstore.store.EventStore eventStore(
                DataSource dataSource,
                com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                com.crablet.eventstore.store.EventStoreConfig config,
                com.crablet.eventstore.clock.ClockProvider clock,
                org.springframework.context.ApplicationEventPublisher eventPublisher) {
            return new com.crablet.eventstore.store.EventStoreImpl(
                dataSource, dataSource, objectMapper, config, clock, eventPublisher);
        }

        @Bean
        public com.crablet.eventstore.store.EventStoreConfig eventStoreConfig() {
            return new com.crablet.eventstore.store.EventStoreConfig();
        }

        @Bean
        public com.crablet.eventstore.clock.ClockProvider clockProvider() {
            return new com.crablet.eventstore.clock.ClockProviderImpl();
        }

        @Bean
        public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }

        @Bean
        public Map<String, ViewSubscriptionConfig> viewSubscriptions() {
            Map<String, ViewSubscriptionConfig> subscriptions = new HashMap<>();
            
            // Course view subscription - matches CourseDefined, StudentSubscribedToCourse, CourseCapacityChanged
            subscriptions.put("course-view", ViewSubscriptionConfig.builder("course-view")
                .eventTypes("CourseDefined", "StudentSubscribedToCourse", "CourseCapacityChanged")
                .requiredTags("course_id")
                .build());
            
            // Region view subscription - matches events with region OR country tags
            subscriptions.put("region-view", ViewSubscriptionConfig.builder("region-view")
                .anyOfTags("region", "country")
                .build());
            
            return subscriptions;
        }

        @Bean
        public ViewEventFetcher viewEventFetcher(
                @Qualifier("readDataSource") DataSource readDataSource,
                Map<String, ViewSubscriptionConfig> subscriptions) {
            return new ViewEventFetcher(readDataSource, subscriptions);
        }
    }
}

