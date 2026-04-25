package com.crablet.command.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the generic REST command API.
 */
@ConfigurationProperties(prefix = "crablet.commands.api")
public class CommandApiProperties {

    /**
     * Base path for the generic REST command endpoint.
     */
    private String basePath = "/api/commands";

    /**
     * Whether the generic command API should accept and echo an HTTP correlation header.
     */
    private boolean correlationHeaderEnabled = false;

    /**
     * Header used for command API correlation when correlationHeaderEnabled is true.
     */
    private String correlationHeaderName = "X-Correlation-Id";

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public boolean isCorrelationHeaderEnabled() {
        return correlationHeaderEnabled;
    }

    public void setCorrelationHeaderEnabled(boolean correlationHeaderEnabled) {
        this.correlationHeaderEnabled = correlationHeaderEnabled;
    }

    public String getCorrelationHeaderName() {
        return correlationHeaderName;
    }

    public void setCorrelationHeaderName(String correlationHeaderName) {
        if (correlationHeaderName != null && !correlationHeaderName.isBlank()) {
            this.correlationHeaderName = correlationHeaderName;
        }
    }
}
