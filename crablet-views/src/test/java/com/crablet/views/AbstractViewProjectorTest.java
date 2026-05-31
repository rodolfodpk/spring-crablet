package com.crablet.views;

import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.CorrelationContext;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.WriteDataSource;
import com.crablet.eventstore.internal.ClockProviderImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NullAway")
@ExtendWith(MockitoExtension.class)
@DisplayName("AbstractViewProjector Unit Tests")
class AbstractViewProjectorTest {

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private DataSource dataSource;

    private ObjectMapper objectMapper;
    private ClockProvider clockProvider;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        clockProvider = new ClockProviderImpl();
        clockProvider.setClock(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Should process events inside transaction and count handled events")
    void shouldProcessEventsInsideTransaction_AndCountHandledEvents() {
        // Given
        stubTransaction();
        TestProjector projector = projector(HandleMode.SKIP_SECOND);
        List<StoredEvent> events = List.of(
                event("WalletOpened", "{\"walletId\":\"wallet-1\"}", 1),
                event("IgnoredEvent", "{\"walletId\":\"wallet-2\"}", 2),
                event("DepositMade", "{\"walletId\":\"wallet-3\"}", 3)
        );

        // When
        int handled = projector.handle("wallet-view", events);

        // Then
        assertThat(handled).isEqualTo(2);
        assertThat(projector.seenTypes).containsExactly("WalletOpened", "IgnoredEvent", "DepositMade");
        verify(transactionManager).commit(any(SimpleTransactionStatus.class));
    }

    @Test
    @DisplayName("Should roll back transaction when event handling fails")
    void shouldRollBackTransaction_WhenEventHandlingFails() {
        // Given
        stubTransaction();
        TestProjector projector = projector(HandleMode.FAIL_FIRST);

        // Then
        assertThatThrownBy(() -> projector.handle("wallet-view",
                List.of(event("WalletOpened", "{\"walletId\":\"wallet-1\"}", 1))))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to project event: WalletOpened")
                .hasCauseInstanceOf(IllegalStateException.class);
        verify(transactionManager).rollback(any(SimpleTransactionStatus.class));
    }

    @Test
    @DisplayName("Should deserialize event payloads")
    void shouldDeserializeEventPayloads() {
        // Given
        TestProjector projector = projector(HandleMode.ALL);

        // When
        Payload payload = projector.deserializePayload(
                event("WalletOpened", "{\"walletId\":\"wallet-1\"}", 1));

        // Then
        assertThat(payload.walletId()).isEqualTo("wallet-1");
    }

    @Test
    @DisplayName("Should wrap deserialization failures")
    void shouldWrapDeserializationFailures() {
        // Given
        TestProjector projector = projector(HandleMode.ALL);

        // Then
        assertThatThrownBy(() -> projector.deserializePayload(
                event("WalletOpened", "not-json", 1)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to deserialize event: WalletOpened to Payload");
    }

    @Test
    @DisplayName("Should bind correlation context while handling each event")
    void shouldBindCorrelationContext_WhileHandlingEachEvent() {
        // Given
        stubTransaction();
        TestProjector projector = projector(HandleMode.ALL);
        UUID correlationId = UUID.randomUUID();
        List<StoredEvent> events = List.of(
                event("WalletOpened", "{\"walletId\":\"wallet-1\"}", 10, correlationId, null),
                event("DepositMade", "{\"walletId\":\"wallet-1\"}", 11, null, null)
        );

        // When
        int handled = projector.handle("wallet-view", events);

        // Then
        assertThat(handled).isEqualTo(2);
        assertThat(projector.seenCorrelationIds).containsExactly(correlationId, null);
        assertThat(projector.seenCausationIds).containsExactly(10L, 11L);
        assertThat(CorrelationContext.correlationId()).isNull();
        assertThat(CorrelationContext.causationId()).isNull();
    }

    private TestProjector projector(HandleMode mode) {
        return new TestProjector(
                objectMapper,
                clockProvider,
                transactionManager,
                new WriteDataSource(dataSource),
                mode
        );
    }

    private void stubTransaction() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
    }

    private StoredEvent event(String type, String json, long position) {
        return new StoredEvent(
                type,
                List.of(),
                json.getBytes(StandardCharsets.UTF_8),
                "tx-" + position,
                position,
                clockProvider.now()
        );
    }

    private StoredEvent event(
            String type,
            String json,
            long position,
            UUID correlationId,
            Long causationId) {
        return new StoredEvent(
                type,
                List.of(),
                json.getBytes(StandardCharsets.UTF_8),
                "tx-" + position,
                position,
                clockProvider.now(),
                correlationId,
                causationId
        );
    }

    private enum HandleMode {
        ALL,
        SKIP_SECOND,
        FAIL_FIRST
    }

    private record Payload(String walletId) {
    }

    private static final class TestProjector extends AbstractViewProjector {
        private final HandleMode mode;
        private final List<String> seenTypes = new ArrayList<>();
        private final List<UUID> seenCorrelationIds = new ArrayList<>();
        private final List<Long> seenCausationIds = new ArrayList<>();

        private TestProjector(
                ObjectMapper objectMapper,
                ClockProvider clockProvider,
                PlatformTransactionManager transactionManager,
                WriteDataSource writeDataSource,
                HandleMode mode) {
            super(objectMapper, clockProvider, transactionManager, writeDataSource);
            this.mode = mode;
        }

        @Override
        public String getViewName() {
            return "wallet-view";
        }

        @Override
        protected boolean handleEvent(StoredEvent event, JdbcTemplate jdbcTemplate) {
            seenTypes.add(event.type());
            seenCorrelationIds.add(CorrelationContext.correlationId());
            seenCausationIds.add(CorrelationContext.causationId());
            if (mode == HandleMode.FAIL_FIRST) {
                throw new IllegalStateException("projection failed");
            }
            return mode != HandleMode.SKIP_SECOND || seenTypes.size() != 2;
        }

        private Payload deserializePayload(StoredEvent event) {
            return deserialize(event, Payload.class);
        }
    }
}
