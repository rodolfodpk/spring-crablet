/**
 * Period segmentation for "Closing the Books" pattern.
 * <p>
 * This package provides support for segmenting events by time periods (hourly, daily,
 * monthly, yearly) to improve query performance for large event histories. Period
 * segmentation is an event organization strategy that tags events with period metadata,
 * allowing queries to filter by period.
 * <p>
 * <strong>Key Components:</strong>
 * <ul>
 *   <li>{@link com.crablet.eventstore.period.PeriodType} - Enum defining period granularity (HOURLY, DAILY, MONTHLY, YEARLY, NONE)</li>
 *   <li>{@link com.crablet.eventstore.period.PeriodConfig} - Annotation to configure period type for commands</li>
 * </ul>
 * <p>
 * <strong>Usage Pattern:</strong>
 * Period segmentation is an opt-in feature. To enable it:
 * <ol>
 *   <li>Annotate your command interface or class with {@code @PeriodConfig(PeriodType.MONTHLY)}</li>
 *   <li>In your command handler, add period tags to events (year, month, day, hour)</li>
 *   <li>Queries can filter by period tags to improve performance</li>
 * </ol>
 * <p>
 * <strong>Framework vs. Domain Responsibilities:</strong>
 * <ul>
 *   <li><strong>Framework:</strong> Provides {@code PeriodType} enum and {@code @PeriodConfig} annotation</li>
 *   <li><strong>Domain:</strong> Implements period tag generation logic (e.g., {@code WalletPeriodHelper})</li>
 *   <li><strong>Domain:</strong> Adds period tags to events in command handlers</li>
 * </ul>
 * <p>
 * <strong>Benefits:</strong>
 * <ul>
 *   <li>Improved query performance for large event histories</li>
 *   <li>Natural organization of events by time periods</li>
 *   <li>Support for "closing the books" scenarios (e.g., monthly statements)</li>
 * </ul>
 * <p>
 * <strong>Note:</strong>
 * Period segmentation is optional. Commands work normally without it, defaulting
 * to {@code PeriodType.NONE} (no period segmentation).
 *
 * @see com.crablet.eventstore.store.EventStore
 * @see com.crablet.eventstore.query.Query
 */
package com.crablet.eventstore.period;

