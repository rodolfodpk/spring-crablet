package com.crablet.examples.wallet.domain.period;

import org.springframework.stereotype.Component;

/**
 * Component that reads period configuration from command class annotations.
 * <p>
 * Reads {@link PeriodConfig} annotation from command classes or their parent interfaces
 * to determine the period type for wallet operations.
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
     * Returns MONTHLY as default if no annotation is found.
     *
     * @param commandClass The command class to check
     * @return The period type configured for this command, or MONTHLY if not specified
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

        // Default to MONTHLY
        return PeriodType.MONTHLY;
    }
}

