package com.crablet.automations.management;

import com.crablet.eventpoller.management.ProcessorManagementService;
import com.crablet.eventpoller.progress.ProcessorStatus;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.internal.ClockProviderImpl;
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
import java.util.HashMap;
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
@DisplayName("AutomationManagementService Unit Tests")
class AutomationManagementServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant LAST_ERROR_AT = Instant.parse("2026-01-01T00:01:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-01T00:02:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:03:00Z");

    @Mock
    private ProcessorManagementService<String> delegate;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement statement;

    @Mock
    private ResultSet resultSet;

    private AutomationManagementService service;

    @BeforeEach
    void setUp() {
        ClockProvider clockProvider = new ClockProviderImpl();
        clockProvider.setClock(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
        service = new AutomationManagementService(delegate, dataSource, clockProvider);
    }

    @Test
    @DisplayName("Should reject null constructor arguments")
    void shouldRejectNullConstructorArguments() {
        assertThatThrownBy(() -> new AutomationManagementService(null, dataSource))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("delegate must not be null");
        assertThatThrownBy(() -> new AutomationManagementService(delegate, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dataSource must not be null");
        assertThatThrownBy(() -> new AutomationManagementService(delegate, dataSource, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("clockProvider must not be null");
    }

    @Test
    @DisplayName("Should delegate processor management operations")
    void shouldDelegateProcessorManagementOperations() {
        // Given
        ProcessorManagementService.BackoffInfo backoffInfo =
                new ProcessorManagementService.BackoffInfo(2, 1);
        Map<String, ProcessorStatus> statuses = Map.of("welcome-email", ProcessorStatus.PAUSED);
        Map<String, ProcessorManagementService.BackoffInfo> backoffInfos =
                Map.of("welcome-email", backoffInfo);
        when(delegate.pause("welcome-email")).thenReturn(true);
        when(delegate.resume("welcome-email")).thenReturn(true);
        when(delegate.reset("welcome-email")).thenReturn(true);
        when(delegate.getStatus("welcome-email")).thenReturn(ProcessorStatus.PAUSED);
        when(delegate.getAllStatuses()).thenReturn(statuses);
        when(delegate.getLag("welcome-email")).thenReturn(8L);
        when(delegate.getBackoffInfo("welcome-email")).thenReturn(backoffInfo);
        when(delegate.getAllBackoffInfo()).thenReturn(backoffInfos);

        // Then
        assertThat(service.pause("welcome-email")).isTrue();
        assertThat(service.resume("welcome-email")).isTrue();
        assertThat(service.reset("welcome-email")).isTrue();
        assertThat(service.getStatus("welcome-email")).isEqualTo(ProcessorStatus.PAUSED);
        assertThat(service.getAllStatuses()).isSameAs(statuses);
        assertThat(service.getLag("welcome-email")).isEqualTo(8L);
        assertThat(service.getBackoffInfo("welcome-email")).isSameAs(backoffInfo);
        assertThat(service.getAllBackoffInfo()).isSameAs(backoffInfos);
    }

    @Test
    @DisplayName("Should return null when automation progress is not found")
    void shouldReturnNull_WhenProgressIsNotFound() throws Exception {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        // When
        AutomationProgressDetails details = service.getProgressDetails("welcome-email");

        // Then
        assertThat(details).isNull();
        verify(statement).setString(1, "welcome-email");
    }

    @Test
    @DisplayName("Should map automation progress details")
    void shouldMapAutomationProgressDetails() throws Exception {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        stubResultSetRows(resultSet, List.of(Map.of(
                "automation_name", "welcome-email",
                "instance_id", "node-1",
                "status", "FAILED",
                "last_position", 44L,
                "error_count", 3,
                "last_error", "handler failed",
                "last_error_at", Timestamp.from(LAST_ERROR_AT),
                "last_updated_at", Timestamp.from(UPDATED_AT),
                "created_at", Timestamp.from(CREATED_AT)
        )));

        // When
        AutomationProgressDetails details = service.getProgressDetails("welcome-email");

        // Then
        assertThat(details).isEqualTo(new AutomationProgressDetails(
                "welcome-email",
                "node-1",
                ProcessorStatus.FAILED,
                44L,
                3,
                "handler failed",
                LAST_ERROR_AT,
                UPDATED_AT,
                CREATED_AT
        ));
    }

    @Test
    @DisplayName("Should use defaults for nullable automation progress columns")
    void shouldUseDefaults_ForNullableProgressColumns() throws Exception {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        stubResultSetRows(resultSet, List.of(rowWithNulls("welcome-email", 44L)));

        // When
        AutomationProgressDetails details = service.getProgressDetails("welcome-email");

        // Then
        assertThat(details.instanceId()).isNull();
        assertThat(details.status()).isEqualTo(ProcessorStatus.ACTIVE);
        assertThat(details.lastError()).isNull();
        assertThat(details.lastErrorAt()).isNull();
        assertThat(details.lastUpdatedAt()).isEqualTo(FIXED_NOW);
        assertThat(details.createdAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("Should map all automation progress details by automation name")
    void shouldMapAllProgressDetails() throws Exception {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        stubResultSetRows(resultSet, List.of(
                row("first-automation", "node-1", "ACTIVE", 10L),
                row("second-automation", "node-2", "PAUSED", 20L)
        ));

        // When
        Map<String, AutomationProgressDetails> details = service.getAllProgressDetails();

        // Then
        assertThat(details).containsOnlyKeys("first-automation", "second-automation");
        assertThat(details.get("first-automation").status()).isEqualTo(ProcessorStatus.ACTIVE);
        assertThat(details.get("second-automation").lastPosition()).isEqualTo(20L);
    }

    @Test
    @DisplayName("Should wrap SQL exceptions from single progress lookup")
    void shouldWrapSqlExceptions_FromSingleProgressLookup() throws Exception {
        // Given
        SQLException failure = new SQLException("connection failed");
        when(dataSource.getConnection()).thenThrow(failure);

        // Then
        assertThatThrownBy(() -> service.getProgressDetails("welcome-email"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to get automation progress details")
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
                .hasMessage("Failed to get all automation progress details")
                .hasCause(failure);
    }

    private static Map<String, Object> row(String name, String instanceId, String status, long position) {
        return Map.of(
                "automation_name", name,
                "instance_id", instanceId,
                "status", status,
                "last_position", position,
                "error_count", 0,
                "last_error", "",
                "last_error_at", Timestamp.from(LAST_ERROR_AT),
                "last_updated_at", Timestamp.from(UPDATED_AT),
                "created_at", Timestamp.from(CREATED_AT)
        );
    }

    private static Map<String, Object> rowWithNulls(String name, long position) {
        Map<String, Object> row = new HashMap<>();
        row.put("automation_name", name);
        row.put("last_position", position);
        row.put("error_count", 0);
        return row;
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
