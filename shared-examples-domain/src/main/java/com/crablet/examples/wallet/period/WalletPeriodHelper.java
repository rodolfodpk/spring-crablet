package com.crablet.examples.wallet.period;

import com.crablet.eventstore.clock.ClockProvider;
import com.crablet.examples.wallet.period.PeriodConfigurationProvider;
import com.crablet.eventstore.period.PeriodType;
import com.crablet.eventstore.store.EventStore;
import com.crablet.examples.wallet.WalletQueryPatterns;
import com.crablet.examples.wallet.projections.WalletBalanceStateProjector;
import com.crablet.examples.wallet.projections.WalletBalanceState;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.store.Cursor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Convenience wrapper for wallet period operations.
 * <p>
 * Provides a simpler API for command handlers to work with wallet periods.
 * Delegates to WalletStatementPeriodResolver for period resolution logic.
 */
@Component
public class WalletPeriodHelper {

    private final WalletStatementPeriodResolver periodResolver;
    private final PeriodConfigurationProvider configProvider;
    private final WalletBalanceStateProjector balanceProjector;
    private final ClockProvider clockProvider;

    public WalletPeriodHelper(
            WalletStatementPeriodResolver periodResolver,
            PeriodConfigurationProvider configProvider,
            WalletBalanceStateProjector balanceProjector,
            ClockProvider clockProvider) {
        this.periodResolver = periodResolver;
        this.configProvider = configProvider;
        this.balanceProjector = balanceProjector;
        this.clockProvider = clockProvider;
    }

    /**
     * Ensure active period exists and project balance for a wallet.
     * <p>
     * This is the main method used by command handlers. It:
     * 1. Resolves the active period for the wallet (creates statement events if needed)
     * 2. Projects balance using period-aware query
     * 3. Returns both the period ID and projection result
     *
     * @param eventStore The transaction-scoped EventStore (passed as parameter)
     * @param walletId   The wallet ID
     * @param commandClass The command class to determine period type from @PeriodConfig annotation
     * @return PeriodProjectionResult containing period ID and balance projection
     */
    public PeriodProjectionResult ensureActivePeriodAndProject(
            EventStore eventStore,
            String walletId,
            Class<?> commandClass) {
        PeriodType periodType = configProvider.getPeriodType(commandClass);
        
        // Resolve active period (creates statement events if needed)
        WalletStatementId periodId = periodResolver.resolveActivePeriod(eventStore, walletId, periodType);
        
        // Project balance using period-aware query
        Query periodQuery = WalletQueryPatterns.singleWalletActivePeriodDecisionModel(
                walletId, periodId.year(), periodId.month() != null ? periodId.month() : 1);
        
        ProjectionResult<WalletBalanceState> projection = eventStore.project(
                periodQuery,
                Cursor.zero(),
                WalletBalanceState.class,
                List.of(balanceProjector)
        );
        
        return new PeriodProjectionResult(periodId, projection);
    }

    /**
     * Project balance for current period without ensuring period exists.
     * <p>
     * This method:
     * 1. Determines current period from clock (no statement event creation)
     * 2. Projects balance using period-aware query
     * 3. Returns both the period ID and projection result
     * <p>
     * Use this method when you don't need explicit statement management (Closing Books Pattern).
     * Period tags are derived from clock, and balance projection works correctly because
     * transaction events contain cumulative newBalance fields.
     *
     * @param eventStore The transaction-scoped EventStore (passed as parameter)
     * @param walletId   The wallet ID
     * @param commandClass The command class to determine period type from @PeriodConfig annotation
     * @return PeriodProjectionResult containing period ID and balance projection
     */
    public PeriodProjectionResult projectCurrentPeriod(
            EventStore eventStore,
            String walletId,
            Class<?> commandClass) {
        PeriodType periodType = configProvider.getPeriodType(commandClass);
        
        // Get current period from clock (no statement creation)
        Instant now = clockProvider.now();
        WalletStatementId periodId = WalletStatementId.fromInstant(walletId, now, periodType);
        
        // Project balance using period-aware query
        // Note: Query includes WalletOpened (no period tags) + transaction events (with period tags)
        // Balance projection works correctly because transaction events have cumulative newBalance fields
        Query periodQuery = WalletQueryPatterns.singleWalletActivePeriodDecisionModel(
                walletId, periodId.year(), periodId.month() != null ? periodId.month() : 1);
        
        ProjectionResult<WalletBalanceState> projection = eventStore.project(
                periodQuery,
                Cursor.zero(),
                WalletBalanceState.class,
                List.of(balanceProjector)
        );
        
        return new PeriodProjectionResult(periodId, projection);
    }

    /**
     * Result of period resolution and balance projection.
     */
    public record PeriodProjectionResult(
            WalletStatementId periodId,
            ProjectionResult<WalletBalanceState> projection
    ) {
    }
}

