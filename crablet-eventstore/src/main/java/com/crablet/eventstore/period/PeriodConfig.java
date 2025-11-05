package com.crablet.eventstore.period;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to configure period type for event segmentation.
 * <p>
 * This annotation is <strong>optional</strong>. Types without this annotation
 * default to {@link PeriodType#NONE} (no period segmentation).
 * <p>
 * Can be applied to command classes or interfaces to specify how events
 * should be segmented by time periods. This is an opt-in feature - commands
 * work normally without it.
 * <p>
 * Period segmentation is an event organization strategy that tags events
 * with period metadata (year, month, day, hour) allowing queries to filter
 * by period, improving performance for large event histories.
 * <p>
 * Example:
 * <pre>{@code
 * @PeriodConfig(PeriodType.MONTHLY)
 * public interface WalletCommand {
 *     // ...
 * }
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PeriodConfig {
    /**
     * The period type to use for event segmentation.
     * Defaults to NONE if not specified (no period segmentation).
     *
     * @return the period type
     */
    PeriodType value() default PeriodType.NONE;
}

