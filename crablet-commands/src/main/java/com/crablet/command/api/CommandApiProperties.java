package com.crablet.command.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the generic REST command API.
 */
@ConfigurationProperties(prefix = "crablet.commands.api")
public class CommandApiProperties {

    /**
     * Whether the generic REST command endpoint is enabled.
     */
    private boolean enabled;

    /**
     * Base path for the generic REST command endpoint.
     */
    private String basePath = "/api/commands";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }
}
