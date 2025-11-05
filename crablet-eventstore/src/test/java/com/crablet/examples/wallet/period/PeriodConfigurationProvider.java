package com.crablet.examples.wallet.period;

import com.crablet.eventstore.period.PeriodConfig;
import com.crablet.eventstore.period.PeriodType;
import org.springframework.stereotype.Component;

/**
 * Component that reads period configuration from command class annotations.
 * <p>
 * Reads {@link PeriodConfig} annotation from command classes or their parent interfaces
 * to determine the period type for wallet operations.
 * <p>
 * This is an <strong>opt-in feature</strong>. Commands without {@link PeriodConfig}
 * annotation default to {@link PeriodType#NONE} (no period segmentation) and work normally.
 * <p>
 * This is an example implementation for the wallet domain. Users implementing
 * the closing books pattern can create their own PeriodConfigurationProvider.
 */
@Component
public class PeriodConfigurationProvider {

    /**
     * Get the period type for a command class.
     * <p>
     * Checks for {@link PeriodConfig} annotation on:
     * 1. The command class itself
     * 2. Parent interfaces (e.g., WalletCommand interface)
     * <p>
     * Returns NONE as default if no annotation is found.
     *
     * @param commandClass The command class to check
     * @return The period type configured for this command, or NONE if not specified
     */
    public PeriodType getPeriodType(Class<?> commandClass) {
        // Check command class itself
        PeriodConfig annotation = commandClass.getAnnotation(PeriodConfig.class);
        if (annotation != null) {
            return annotation.value();
        }

        // Check parent interfaces
        for (Class<?> iface : commandClass.getInterfaces()) {
            annotation = iface.getAnnotation(PeriodConfig.class);
            if (annotation != null) {
                return annotation.value();
            }
        }

        // Default to NONE
        return PeriodType.NONE;
    }
}

