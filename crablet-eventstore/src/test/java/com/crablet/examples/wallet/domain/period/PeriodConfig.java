package com.crablet.examples.wallet.domain.period;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to configure period type for wallet commands.
 * <p>
 * Can be applied to command classes or interfaces to specify how wallet statements
 * should be segmented. Defaults to MONTHLY if not specified.
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
     * The period type to use for this command.
     * Defaults to MONTHLY if not specified.
     */
    PeriodType value() default PeriodType.MONTHLY;
}

