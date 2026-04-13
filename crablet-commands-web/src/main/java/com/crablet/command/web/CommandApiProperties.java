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

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }
}
