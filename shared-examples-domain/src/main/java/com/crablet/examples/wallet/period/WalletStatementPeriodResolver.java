package com.crablet.examples.wallet.period;

import com.crablet.eventstore.period.PeriodType;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.AppendConditionBuilder;
import com.crablet.eventstore.dcb.ConcurrencyException;
import com.crablet.eventstore.query.EventRepository;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryBuilder;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.examples.wallet.WalletQueryPatterns;
import com.crablet.examples.wallet.events.WalletStatementClosed;
import com.crablet.examples.wallet.events.WalletStatementOpened;
import com.crablet.examples.wallet.events.WalletOpened;
import com.crablet.examples.wallet.events.DepositMade;
import com.crablet.examples.wallet.events.WithdrawalMade;
import com.crablet.examples.wallet.events.MoneyTransferred;
import com.crablet.examples.wallet.projections.WalletBalanceProjector;
import com.crablet.examples.wallet.projections.WalletBalanceState;
import com.crablet.eventstore.clock.ClockProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static com.crablet.eventstore.store.EventType.type;
import static com.crablet.examples.wallet.WalletTags.*;

/**
 * Wallet-specific period resolver for closing the books pattern.
 * <p>
 * Resolves active statement periods and lazily creates statement events when needed.
 * Statement events are appended internally using the transaction-scoped EventStore.
 */
@Component
public class WalletStatementPeriodResolver {

    private final EventRepository eventRepository;
    private final ClockProvider clock;
    private final ObjectMapper objectMapper;
    private final WalletBalanceProjector balanceProjector;

    public WalletStatementPeriodResolver(
            EventRepository eventRepository,
            ClockProvider clock,
            ObjectMapper objectMapper,
            WalletBalanceProjector balanceProjector) {
        this.eventRepository = eventRepository;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.balanceProjector = balanceProjector;
    }

    /**
     * Resolve the active period for a wallet, creating statement events if needed.
     * <p>
     * Checks if current period has WalletStatementOpened event.
     * If not, closes previous period and opens current period atomically.
     * <p>
     * Statement events are appended internally using the provided EventStore.
     *
     * @param eventStore The transaction-scoped EventStore (passed as parameter)
     * @param walletId   The wallet ID
     * @param periodType The period type (DAILY, MONTHLY, YEARLY)
     * @return WalletStatementId for the current active period
     */
    public WalletStatementId resolveActivePeriod(EventStore eventStore, String walletId, PeriodType periodType) {
        Instant now = clock.now();
        WalletStatementId currentPeriod = WalletStatementId.fromInstant(walletId, now, periodType);

        // Check if current period exists
        if (periodExists(eventStore, currentPeriod)) {
            return currentPeriod;
        }

        // Period change detected - close previous period and open new period
        WalletStatementId previousPeriod = calculatePreviousPeriod(currentPeriod, periodType);

        // Close previous period if it exists and hasn't been closed
        if (previousPeriod != null && !periodClosed(eventStore, previousPeriod)) {
            int closingBalance = projectClosingBalance(eventStore, walletId, previousPeriod);
            // Only create close event if there were transactions in that period
            if (hasTransactionsInPeriod(eventStore, walletId, previousPeriod)) {
                WalletStatementClosed closeEvent = createCloseEvent(walletId, previousPeriod, closingBalance);
                appendCloseEvent(eventStore, previousPeriod, closeEvent);
            }
        }

        // Open current period
        int openingBalance = getOpeningBalanceForNewPeriod(eventStore, walletId, previousPeriod);
        WalletStatementOpened openEvent = createOpenEvent(walletId, currentPeriod, openingBalance);
        appendOpenEvent(eventStore, currentPeriod, openEvent);

        return currentPeriod;
    }

    /**
     * Check if a period has WalletStatementOpened event.
     * Uses the transaction-scoped EventStore to see uncommitted events.
     */
    private boolean periodExists(EventStore eventStore, WalletStatementId periodId) {
        // Use project to check if events exist - this works within transactions
        Query query = Query.forEventAndTag(type(WalletStatementOpened.class), STATEMENT_ID, periodId.toStreamId());
        try {
            // Use a simple projector that just checks if any events exist
            ProjectionResult<WalletBalanceState> result = eventStore.project(
                    query,
                    Cursor.zero(),
                    WalletBalanceState.class,
                    List.of(balanceProjector)
            );
            // WalletStatementOpened events set isExisting() to true and walletId
            // If we get here and state exists with non-empty walletId, events were found
            return result.state().isExisting() && !result.state().walletId().isEmpty();
        } catch (Exception e) {
            // If projection fails or returns no events, period doesn't exist
            return false;
        }
    }

    /**
     * Check if a period has been closed (has WalletStatementClosed event).
     * Uses the transaction-scoped EventStore to see uncommitted events.
     */
    private boolean periodClosed(EventStore eventStore, WalletStatementId periodId) {
        // For WalletStatementClosed, we need to check if events exist
        // Since WalletStatementClosed doesn't modify state in projection,
        // we can't rely on state changes. Instead, try to query using EventRepository
        // which should see committed events, and if that fails, try projecting
        Query query = Query.forEventAndTag(type(WalletStatementClosed.class), STATEMENT_ID, periodId.toStreamId());
        try {
            // Try using EventRepository first (sees committed events)
            List<StoredEvent> events = eventRepository.query(query, null);
            if (!events.isEmpty()) {
                return true;
            }
            // If EventRepository doesn't find it, try projecting (sees uncommitted events)
            // This is a fallback - if projection succeeds, events might exist
            ProjectionResult<WalletBalanceState> result = eventStore.project(
                    query,
                    Cursor.zero(),
                    WalletBalanceState.class,
                    List.of(balanceProjector)
            );
            // If cursor advanced, events were processed
            // Initial cursor is zero, so if result.cursor() is not zero, events were found
            return result.cursor().position().value() > 0;
        } catch (Exception e) {
            // If projection fails or returns no events, period isn't closed
            return false;
        }
    }

    /**
     * Project closing balance for a period by querying only that period's events.
     */
    private int projectClosingBalance(EventStore eventStore, String walletId, WalletStatementId periodId) {
        // Query only events from this period using period-aware query
        // periodId.month() returns Integer, convert to int
        Query periodQuery = WalletQueryPatterns.singleWalletPeriodDecisionModel(
                walletId, periodId.year(), periodId.month() != null ? periodId.month() : 1);
        
        ProjectionResult<WalletBalanceState> result = eventStore.project(
                periodQuery,
                Cursor.zero(),
                WalletBalanceState.class,
                List.of(balanceProjector)
        );
        
        return result.state().balance();
    }

    /**
     * Check if there are any transactions in a period.
     */
    private boolean hasTransactionsInPeriod(EventStore eventStore, String walletId, WalletStatementId periodId) {
        Query periodQuery = WalletQueryPatterns.singleWalletPeriodDecisionModel(
                walletId, periodId.year(), periodId.month() != null ? periodId.month() : 1);
        
        List<StoredEvent> events = eventRepository.query(periodQuery, null);
        
        // Filter out WalletStatementOpened events - we only care about actual transactions
        return events.stream()
                .anyMatch(e -> !type(WalletStatementOpened.class).equals(e.type()) && 
                              !type(WalletStatementClosed.class).equals(e.type()));
    }

    /**
     * Get opening balance for new period.
     * Reads from previous period's WalletStatementClosed event, or projects from all events for first period.
     */
    private int getOpeningBalanceForNewPeriod(EventStore eventStore, String walletId, WalletStatementId previousPeriod) {
        if (previousPeriod == null) {
            // First period ever - query all events without period tags
            return getInitialBalanceForFirstPeriod(eventStore, walletId);
        }

        // Read closing balance from previous period's WalletStatementClosed event
        Query query = Query.forEventAndTag("WalletStatementClosed", STATEMENT_ID, previousPeriod.toStreamId());
        List<StoredEvent> events = eventRepository.query(query, null);
        
        if (events.isEmpty()) {
            // Previous period wasn't closed - project balance from all events
            return getInitialBalanceForFirstPeriod(eventStore, walletId);
        }

        // Deserialize WalletStatementClosed event to get closing balance
        try {
            WalletStatementClosed closed = objectMapper.readValue(events.get(0).data(), WalletStatementClosed.class);
            return closed.closingBalance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read closing balance from WalletStatementClosed event", e);
        }
    }

    /**
     * Get initial balance for first period by querying all events without period tags.
     */
    private int getInitialBalanceForFirstPeriod(EventStore eventStore, String walletId) {
        // Query all events without period tags (one-time only for first period)
        Query query = QueryBuilder.create()
                .events(type(WalletOpened.class), type(DepositMade.class), type(WithdrawalMade.class))
                .tag(WALLET_ID, walletId)
                .event(type(MoneyTransferred.class), FROM_WALLET_ID, walletId)
                .event(type(MoneyTransferred.class), TO_WALLET_ID, walletId)
                .build();

        ProjectionResult<WalletBalanceState> result = eventStore.project(
                query,
                Cursor.zero(),
                WalletBalanceState.class,
                List.of(balanceProjector)
        );

        return result.state().balance();
    }

    /**
     * Create WalletStatementClosed event.
     */
    private WalletStatementClosed createCloseEvent(String walletId, WalletStatementId periodId, int closingBalance) {
        // Use default values for month/day/hour if null (required by event structure)
        int month = periodId.month() != null ? periodId.month() : 1;
        int day = periodId.day() != null ? periodId.day() : 1;
        int hour = periodId.hour() != null ? periodId.hour() : 0;
        
        return WalletStatementClosed.of(
                walletId,
                periodId.toStreamId(),
                periodId.year(),
                month,
                day,
                hour,
                closingBalance,
                closingBalance
        );
    }

    /**
     * Create WalletStatementOpened event.
     */
    private WalletStatementOpened createOpenEvent(String walletId, WalletStatementId periodId, int openingBalance) {
        // Use default values for month/day/hour if null (required by event structure)
        int month = periodId.month() != null ? periodId.month() : 1;
        int day = periodId.day() != null ? periodId.day() : 1;
        int hour = periodId.hour() != null ? periodId.hour() : 0;
        
        return WalletStatementOpened.of(
                walletId,
                periodId.toStreamId(),
                periodId.year(),
                month,
                day,
                hour,
                openingBalance
        );
    }

    /**
     * Append WalletStatementClosed event with idempotency check.
     */
    private void appendCloseEvent(EventStore eventStore, WalletStatementId periodId, WalletStatementClosed closeEvent) {
        try {
            byte[] data = objectMapper.writeValueAsBytes(closeEvent);
            AppendEvent.Builder eventBuilder = AppendEvent.builder(type(WalletStatementClosed.class))
                    .tag(STATEMENT_ID, periodId.toStreamId())
                    .tag(WALLET_ID, periodId.walletId())
                    .tag(YEAR, String.valueOf(periodId.year()));
            
            if (periodId.month() != null) {
                eventBuilder.tag(MONTH, String.valueOf(periodId.month()));
            }
            if (periodId.day() != null) {
                eventBuilder.tag(DAY, String.valueOf(periodId.day()));
            }
            if (periodId.hour() != null) {
                eventBuilder.tag(HOUR, String.valueOf(periodId.hour()));
            }
            
            AppendEvent event = eventBuilder.data(data).build();

            // Idempotency check: ensure WalletStatementClosed doesn't already exist for this statement_id
            AppendCondition condition = new AppendConditionBuilder(Query.empty(), Cursor.zero())
                    .withIdempotencyCheck(type(WalletStatementClosed.class), STATEMENT_ID, periodId.toStreamId())
                    .build();

            eventStore.appendIf(List.of(event), condition);
        } catch (ConcurrencyException e) {
            // Idempotency violation means event already exists - this is fine, it's idempotent
            // The idempotency check throws ConcurrencyException when the event already exists
            // We can safely treat this as success - the event is already there
            return; // Success - event already exists (idempotent operation)
        } catch (Exception e) {
            throw new RuntimeException("Failed to append WalletStatementClosed event", e);
        }
    }

    /**
     * Append WalletStatementOpened event with idempotency check.
     */
    private void appendOpenEvent(EventStore eventStore, WalletStatementId periodId, WalletStatementOpened openEvent) {
        try {
            byte[] data = objectMapper.writeValueAsBytes(openEvent);
            AppendEvent.Builder eventBuilder = AppendEvent.builder(type(WalletStatementOpened.class))
                    .tag(STATEMENT_ID, periodId.toStreamId())
                    .tag(WALLET_ID, periodId.walletId())
                    .tag(YEAR, String.valueOf(periodId.year()));
            
            if (periodId.month() != null) {
                eventBuilder.tag(MONTH, String.valueOf(periodId.month()));
            }
            if (periodId.day() != null) {
                eventBuilder.tag(DAY, String.valueOf(periodId.day()));
            }
            if (periodId.hour() != null) {
                eventBuilder.tag(HOUR, String.valueOf(periodId.hour()));
            }
            
            AppendEvent event = eventBuilder.data(data).build();

            // Idempotency check: ensure WalletStatementOpened doesn't already exist for this statement_id
            AppendCondition condition = new AppendConditionBuilder(Query.empty(), Cursor.zero())
                    .withIdempotencyCheck(type(WalletStatementOpened.class), STATEMENT_ID, periodId.toStreamId())
                    .build();

            eventStore.appendIf(List.of(event), condition);
        } catch (ConcurrencyException e) {
            // Idempotency violation means event already exists - this is fine, it's idempotent
            // The idempotency check throws ConcurrencyException when the event already exists
            // We can safely treat this as success - the event is already there
            return; // Success - event already exists (idempotent operation)
        } catch (Exception e) {
            throw new RuntimeException("Failed to append WalletStatementOpened event", e);
        }
    }

    /**
     * Calculate previous period ID.
     */
    private WalletStatementId calculatePreviousPeriod(WalletStatementId currentPeriod, PeriodType periodType) {
        if (periodType == PeriodType.NONE) {
            return null;
        }

        return switch (periodType) {
            case HOURLY -> {
                LocalDateTime currentDateTime = LocalDateTime.of(
                        currentPeriod.year(),
                        currentPeriod.month() != null ? currentPeriod.month() : 1,
                        currentPeriod.day() != null ? currentPeriod.day() : 1,
                        currentPeriod.hour() != null ? currentPeriod.hour() : 0,
                        0, 0, 0  // minutes, seconds, nanoseconds
                );
                LocalDateTime prevDateTime = currentDateTime.minusHours(1);
                yield WalletStatementId.ofHourly(
                        currentPeriod.walletId(),
                        prevDateTime.getYear(),
                        prevDateTime.getMonthValue(),
                        prevDateTime.getDayOfMonth(),
                        prevDateTime.getHour()
                );
            }
            case DAILY -> {
                LocalDate currentDate = LocalDate.of(
                        currentPeriod.year(),
                        currentPeriod.month() != null ? currentPeriod.month() : 1,
                        currentPeriod.day() != null ? currentPeriod.day() : 1
                );
                LocalDate prevDate = currentDate.minusDays(1);
                yield WalletStatementId.ofDaily(
                        currentPeriod.walletId(),
                        prevDate.getYear(),
                        prevDate.getMonthValue(),
                        prevDate.getDayOfMonth()
                );
            }
            case MONTHLY -> {
                YearMonth prevMonth = YearMonth.of(currentPeriod.year(), currentPeriod.month()).minusMonths(1);
                yield WalletStatementId.fromYearMonth(currentPeriod.walletId(), prevMonth);
            }
            case YEARLY -> {
                int prevYear = currentPeriod.year() - 1;
                yield WalletStatementId.ofYearly(currentPeriod.walletId(), prevYear);
            }
            case NONE -> null;
        };
    }
}

