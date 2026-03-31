package com.crablet.eventstore.period;

import com.crablet.eventstore.store.Tag;

import java.util.List;

/**
 * Framework utility for building standard period tags from the closing-the-books pattern.
 * <p>
 * Provides exhaustive, type-safe tag construction for each {@link PeriodType}.
 * Domain code delegates to these methods from their own period identifier types,
 * keeping period tag key names and granularity logic out of business code.
 * <p>
 * Standard tag keys are defined as constants here — domain code should import them
 * rather than defining their own string literals.
 *
 * <pre>{@code
 * // Domain period type delegates to framework:
 * public List<Tag> asTags() {
 *     return switch (periodType()) {
 *         case YEARLY  -> PeriodTags.yearly(year);
 *         case MONTHLY -> PeriodTags.monthly(year, month);
 *         case DAILY   -> PeriodTags.daily(year, month, day);
 *         case HOURLY  -> PeriodTags.hourly(year, month, day, hour);
 *         case NONE    -> List.of();
 *     };
 * }
 *
 * // Handler uses it:
 * AppendEvent event = AppendEvent.builder(type(DepositMade.class))
 *         .tag(WALLET_ID, command.walletId())
 *         .tags(periodId.asTags())
 *         .data(deposit)
 *         .build();
 * }</pre>
 */
public final class PeriodTags {

    public static final String YEAR  = "year";
    public static final String MONTH = "month";
    public static final String DAY   = "day";
    public static final String HOUR  = "hour";

    private PeriodTags() {}

    /** Tags for a yearly period — only {@code year}. */
    public static List<Tag> yearly(int year) {
        return List.of(tag(YEAR, year));
    }

    /** Tags for a monthly period — {@code year} and {@code month}. */
    public static List<Tag> monthly(int year, int month) {
        return List.of(tag(YEAR, year), tag(MONTH, month));
    }

    /** Tags for a daily period — {@code year}, {@code month}, and {@code day}. */
    public static List<Tag> daily(int year, int month, int day) {
        return List.of(tag(YEAR, year), tag(MONTH, month), tag(DAY, day));
    }

    /** Tags for an hourly period — {@code year}, {@code month}, {@code day}, and {@code hour}. */
    public static List<Tag> hourly(int year, int month, int day, int hour) {
        return List.of(tag(YEAR, year), tag(MONTH, month), tag(DAY, day), tag(HOUR, hour));
    }

    private static Tag tag(String key, int value) {
        return new Tag(key, String.valueOf(value));
    }
}
