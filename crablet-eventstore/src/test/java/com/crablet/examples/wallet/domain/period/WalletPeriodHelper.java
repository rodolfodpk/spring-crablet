package com.crablet.examples.wallet.domain.period;

import com.crablet.eventstore.store.EventStore;
import com.crablet.examples.wallet.domain.WalletQueryPatterns;
import com.crablet.examples.wallet.domain.projections.WalletBalanceProjector;
import com.crablet.examples.wallet.domain.projections.WalletBalanceState;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.store.Cursor;
import org.springframework.stereotype.Component;

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
    private final WalletBalanceProjector balanceProjector;

    public WalletPeriodHelper(
            WalletStatementPeriodResolver periodResolver,
            PeriodConfigurationProvider configProvider,
            WalletBalanceProjector balanceProjector) {
        this.periodResolver = periodResolver;
        this.configProvider = configProvider;
        this.balanceProjector = balanceProjector;
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
     * Result of period resolution and balance projection.
     */
    public record PeriodProjectionResult(
            WalletStatementId periodId,
            ProjectionResult<WalletBalanceState> projection
    ) {
    }
}

