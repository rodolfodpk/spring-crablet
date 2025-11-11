package com.crablet.command.handlers.wallet.unit;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.period.PeriodType;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.store.*;
import com.crablet.examples.wallet.WalletQueryPatterns;
import com.crablet.examples.wallet.event.WalletStatementOpened;
import com.crablet.examples.wallet.period.WalletPeriodHelper;
import com.crablet.examples.wallet.period.WalletStatementId;
import com.crablet.examples.wallet.period.WalletStatementPeriodResolver;
import com.crablet.examples.wallet.period.PeriodConfigurationProvider;
import com.crablet.examples.wallet.projections.WalletBalanceProjector;
import com.crablet.examples.wallet.projections.WalletBalanceState;

import java.util.List;

import static com.crablet.examples.wallet.WalletEventTypes.WALLET_STATEMENT_OPENED;
import static com.crablet.examples.wallet.WalletTags.STATEMENT_ID;
import static com.crablet.examples.wallet.WalletTags.WALLET_ID;
import static com.crablet.examples.wallet.WalletTags.YEAR;
import static com.crablet.examples.wallet.WalletTags.MONTH;

/**
 * Factory for creating test-friendly {@link WalletPeriodHelper} instances for unit tests.
 * <p>
 * Provides simplified period resolution that works with {@link InMemoryEventStore}:
 * <ul>
 *   <li>Always uses fixed monthly period (2025-01) - no clock dependency</li>
 *   <li>Uses {@code EventStore.project()} instead of {@code EventRepository}</li>
 *   <li>No ObjectMapper - works with direct object storage</li>
 *   <li>Creates {@code WalletStatementOpened} automatically if period doesn't exist</li>
 * </ul>
 * <p>
 * <strong>Usage:</strong>
 * <pre>{@code
 * WalletPeriodHelper periodHelper = WalletPeriodHelperTestFactory.createTestHelper(eventStore);
 * DepositCommandHandler handler = new DepositCommandHandler(periodHelper);
 * }</pre>
 */
public class WalletPeriodHelperTestFactory {
    
    /**
     * Create a test-friendly {@link WalletPeriodHelper} for unit tests.
     * 
     * @param eventStore The in-memory event store
     * @return Configured WalletPeriodHelper for unit tests
     */
    public static WalletPeriodHelper createTestHelper(EventStore eventStore) {
        // Create simplified resolver that works with InMemoryEventStore
        TestWalletStatementPeriodResolver periodResolver = new TestWalletStatementPeriodResolver(eventStore);
        
        // Create simplified config provider that always returns MONTHLY
        TestPeriodConfigurationProvider configProvider = new TestPeriodConfigurationProvider();
        
        return new WalletPeriodHelper(
            periodResolver,
            configProvider,
            new WalletBalanceProjector()
        );
    }
    
    /**
     * Simplified WalletStatementPeriodResolver for unit tests.
     * Works with InMemoryEventStore (no EventRepository, no ObjectMapper, no ClockProvider).
     */
    private static class TestWalletStatementPeriodResolver extends WalletStatementPeriodResolver {
        public TestWalletStatementPeriodResolver(EventStore eventStore) {
            // Pass nulls - we override resolveActivePeriod to not use them
            super(null, null, null, new WalletBalanceProjector());
        }
        
        @Override
        public WalletStatementId resolveActivePeriod(EventStore store, String walletId, PeriodType periodType) {
            // Always return fixed period (2025-01)
            WalletStatementId periodId = WalletStatementId.ofMonthly(walletId, 2025, 1);
            
            // Check if WalletStatementOpened exists for this period
            if (!periodExists(store, periodId)) {
                // Create WalletStatementOpened with opening balance from projection
                int openingBalance = getOpeningBalance(store, walletId);
                createWalletStatementOpened(store, periodId, openingBalance);
            }
            
            return periodId;
        }
        
        private boolean periodExists(EventStore eventStore, WalletStatementId periodId) {
            Query query = Query.forEventAndTag(WALLET_STATEMENT_OPENED, STATEMENT_ID, periodId.toStreamId());
            try {
                WalletBalanceProjector projector = new WalletBalanceProjector();
                ProjectionResult<WalletBalanceState> result = eventStore.project(
                    query, Cursor.zero(), WalletBalanceState.class, List.of(projector));
                return result.state().isExisting() && !result.state().walletId().isEmpty();
            } catch (Exception e) {
                return false;
            }
        }
        
        private int getOpeningBalance(EventStore eventStore, String walletId) {
            Query query = WalletQueryPatterns.singleWalletDecisionModel(walletId);
            WalletBalanceProjector projector = new WalletBalanceProjector();
            ProjectionResult<WalletBalanceState> result = eventStore.project(
                query, Cursor.zero(), WalletBalanceState.class, List.of(projector));
            return result.state().balance();
        }
        
        private void createWalletStatementOpened(
                EventStore eventStore,
                WalletStatementId periodId,
                int openingBalance) {
            WalletStatementOpened opened = WalletStatementOpened.of(
                periodId.walletId(),
                periodId.toStreamId(),
                periodId.year(),
                periodId.month(),
                periodId.day(),
                periodId.hour(),
                openingBalance
            );
            
            AppendEvent.Builder builder = AppendEvent.builder(WALLET_STATEMENT_OPENED)
                .data(opened)
                .tag(STATEMENT_ID, periodId.toStreamId())
                .tag(WALLET_ID, periodId.walletId())
                .tag(YEAR, String.valueOf(periodId.year()));
            
            if (periodId.month() != null) {
                builder.tag(MONTH, String.valueOf(periodId.month()));
            }
            if (periodId.day() != null) {
                builder.tag("day", String.valueOf(periodId.day()));
            }
            if (periodId.hour() != null) {
                builder.tag("hour", String.valueOf(periodId.hour()));
            }
            
            AppendEvent event = builder.build();
            eventStore.appendIf(List.of(event), AppendCondition.empty());
        }
    }
    
    /**
     * Simplified PeriodConfigurationProvider for unit tests.
     * Always returns MONTHLY period type.
     */
    private static class TestPeriodConfigurationProvider extends PeriodConfigurationProvider {
        @Override
        public PeriodType getPeriodType(Class<?> commandClass) {
            return PeriodType.MONTHLY; // Always MONTHLY for tests
        }
    }
}

