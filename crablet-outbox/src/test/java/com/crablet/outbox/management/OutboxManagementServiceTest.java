package com.crablet.outbox.management;

import com.crablet.eventpoller.management.ProcessorManagementService;
import com.crablet.eventpoller.progress.ProcessorStatus;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.internal.ClockProviderImpl;
import com.crablet.outbox.TopicPublisherPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NullAway")
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxManagementService Unit Tests")
class OutboxManagementServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant LAST_PUBLISHED_AT = Instant.parse("2026-01-01T00:01:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-01T00:02:00Z");

    @Mock
    private ProcessorManagementService<TopicPublisherPair> delegate;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement statement;

    @Mock
    private ResultSet resultSet;

    private TopicPublisherPair pair;
    private OutboxManagementService service;

    @BeforeEach
    void setUp() {
        ClockProvider clockProvider = new ClockProviderImpl();
        clockProvider.setClock(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
        pair = new TopicPublisherPair("wallet", "kafka");
        service = new OutboxManagementService(delegate, dataSource, clockProvider);
    }

    @Test
    @DisplayName("Should reject null constructor arguments")
    void shouldRejectNullConstructorArguments() {
        assertThatThrownBy(() -> new OutboxManagementService(null, dataSource))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("delegate must not be null");
        assertThatThrownBy(() -> new OutboxManagementService(delegate, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dataSource must not be null");
        assertThatThrownBy(() -> new OutboxManagementService(delegate, dataSource, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("clockProvider must not be null");
    }

    @Test
    @DisplayName("Should delegate processor management operations")
    void shouldDelegateProcessorManagementOperations() {
        // Given
        ProcessorManagementService.BackoffInfo backoffInfo =
                new ProcessorManagementService.BackoffInfo(3, 2);
        Map<TopicPublisherPair, ProcessorStatus> statuses = Map.of(pair, ProcessorStatus.PAUSED);
        Map<TopicPublisherPair, ProcessorManagementService.BackoffInfo> backoffInfos =
                Map.of(pair, backoffInfo);
        when(delegate.pause(pair)).thenReturn(true);
        when(delegate.resume(pair)).thenReturn(true);
        when(delegate.reset(pair)).thenReturn(true);
        when(delegate.getStatus(pair)).thenReturn(ProcessorStatus.PAUSED);
        when(delegate.getAllStatuses()).thenReturn(statuses);
        when(delegate.getLag(pair)).thenReturn(42L);
        when(delegate.getBackoffInfo(pair)).thenReturn(backoffInfo);
        when(delegate.getAllBackoffInfo()).thenReturn(backoffInfos);

        // Then
        assertThat(service.pause(pair)).isTrue();
        assertThat(service.resume(pair)).isTrue();
        assertThat(service.reset(pair)).isTrue();
        assertThat(service.getStatus(pair)).isEqualTo(ProcessorStatus.PAUSED);
        assertThat(service.getAllStatuses()).isSameAs(statuses);
        assertThat(service.getLag(pair)).isEqualTo(42L);
        assertThat(service.getBackoffInfo(pair)).isSameAs(backoffInfo);
        assertThat(service.getAllBackoffInfo()).isSameAs(backoffInfos);
    }

    @Test
    @DisplayName("Should return null when progress details are not found")
    void shouldReturnNull_WhenProgressDetailsAreNotFound() throws Exception {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        // When
        OutboxProgressDetails details = service.getProgressDetails("wallet", "kafka");

        // Then
        assertThat(details).isNull();
        verify(statement).setString(1, "wallet");
        verify(statement).setString(2, "kafka");
    }

    @Test
    @DisplayName("Should map progress details from result set")
    void shouldMapProgressDetails_FromResultSet() throws Exception {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        stubResultSetRows(resultSet, List.of(Map.of(
                "topic", "wallet",
                "publisher", "kafka",
                "status", "FAILED",
                "last_position", 123L,
                "last_published_at", Timestamp.from(LAST_PUBLISHED_AT),
                "error_count", 7,
                "last_error", "publish failed",
                "updated_at", Timestamp.from(UPDATED_AT),
                "leader_instance", "node-1"
        )));

        // When
        OutboxProgressDetails details = service.getProgressDetails("wallet", "kafka");

        // Then
        assertThat(details).isEqualTo(new OutboxProgressDetails(
                "wallet",
                "kafka",
                ProcessorStatus.FAILED,
                123L,
                LAST_PUBLISHED_AT,
                7,
                "publish failed",
                UPDATED_AT,
                "node-1"
        ));
    }

    @Test
    @DisplayName("Should use defaults for nullable progress columns")
    void shouldUseDefaults_ForNullableProgressColumns() throws Exception {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        stubResultSetRows(resultSet, List.of(rowWithNulls()));

        // When
        OutboxProgressDetails details = service.getProgressDetails("wallet", "kafka");

        // Then
        assertThat(details.status()).isEqualTo(ProcessorStatus.ACTIVE);
        assertThat(details.lastPublishedAt()).isNull();
        assertThat(details.lastError()).isNull();
        assertThat(details.updatedAt()).isEqualTo(FIXED_NOW);
        assertThat(details.leaderInstance()).isNull();
    }

    @Test
    @DisplayName("Should map all progress details in topic and publisher order")
    void shouldMapAllProgressDetails() throws Exception {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        stubResultSetRows(resultSet, List.of(
                Map.of(
                        "topic", "ledger",
                        "publisher", "audit",
                        "status", "ACTIVE",
                        "last_position", 10L,
                        "last_published_at", Timestamp.from(LAST_PUBLISHED_AT),
                        "error_count", 0,
                        "last_error", "",
                        "updated_at", Timestamp.from(UPDATED_AT),
                        "leader_instance", "node-1"),
                Map.of(
                        "topic", "wallet",
                        "publisher", "kafka",
                        "status", "PAUSED",
                        "last_position", 20L,
                        "last_published_at", Timestamp.from(LAST_PUBLISHED_AT.plusSeconds(1)),
                        "error_count", 1,
                        "last_error", "paused by operator",
                        "updated_at", Timestamp.from(UPDATED_AT.plusSeconds(1)),
                        "leader_instance", "node-2")
        ));

        // When
        List<OutboxProgressDetails> details = service.getAllProgressDetails();

        // Then
        assertThat(details)
                .extracting(OutboxProgressDetails::topic, OutboxProgressDetails::publisher,
                        OutboxProgressDetails::status, OutboxProgressDetails::lastPosition)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ledger", "audit", ProcessorStatus.ACTIVE, 10L),
                        org.assertj.core.groups.Tuple.tuple("wallet", "kafka", ProcessorStatus.PAUSED, 20L)
                );
    }

    @Test
    @DisplayName("Should wrap SQL exceptions from single progress lookup")
    void shouldWrapSqlExceptions_FromSingleProgressLookup() throws Exception {
        // Given
        SQLException failure = new SQLException("connection failed");
        when(dataSource.getConnection()).thenThrow(failure);

        // Then
        assertThatThrownBy(() -> service.getProgressDetails("wallet", "kafka"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to get outbox progress details")
                .hasCause(failure);
    }

    @Test
    @DisplayName("Should wrap SQL exceptions from all progress lookup")
    void shouldWrapSqlExceptions_FromAllProgressLookup() throws Exception {
        // Given
        SQLException failure = new SQLException("connection failed");
        when(dataSource.getConnection()).thenThrow(failure);

        // Then
        assertThatThrownBy(() -> service.getAllProgressDetails())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to get all outbox progress details")
                .hasCause(failure);
    }

    private static Map<String, Object> rowWithNulls() {
        return new java.util.HashMap<>(Map.of(
                "topic", "wallet",
                "publisher", "kafka",
                "last_position", 123L,
                "error_count", 0
        ));
    }

    private static void stubResultSetRows(ResultSet resultSet, List<Map<String, Object>> rows)
            throws SQLException {
        AtomicInteger rowIndex = new AtomicInteger(-1);
        AtomicBoolean lastValueWasNull = new AtomicBoolean(false);

        when(resultSet.next()).thenAnswer(_invocation -> rowIndex.incrementAndGet() < rows.size());
        when(resultSet.getString(anyString())).thenAnswer(invocation -> {
            Object value = rows.get(rowIndex.get()).get(invocation.getArgument(0, String.class));
            lastValueWasNull.set(value == null);
            return (String) value;
        });
        when(resultSet.getLong(anyString())).thenAnswer(invocation -> {
            Object value = rows.get(rowIndex.get()).get(invocation.getArgument(0, String.class));
            lastValueWasNull.set(value == null);
            return value == null ? 0L : ((Number) value).longValue();
        });
        when(resultSet.getInt(anyString())).thenAnswer(invocation -> {
            Object value = rows.get(rowIndex.get()).get(invocation.getArgument(0, String.class));
            lastValueWasNull.set(value == null);
            return value == null ? 0 : ((Number) value).intValue();
        });
        when(resultSet.getTimestamp(anyString())).thenAnswer(invocation -> {
            Object value = rows.get(rowIndex.get()).get(invocation.getArgument(0, String.class));
            lastValueWasNull.set(value == null);
            return (Timestamp) value;
        });
        when(resultSet.wasNull()).thenAnswer(_invocation -> lastValueWasNull.get());
    }
}
