package com.crablet.eventstore.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for DataSource configuration.
 * Binds to spring.datasource.* properties.
 */
@ConfigurationProperties(prefix = "spring.datasource")
public class DataSourceConfigProperties {
    private @Nullable String url;
    private @Nullable String username;
    private @Nullable String password;
    private @Nullable String driverClassName;

    public @Nullable String getUrl() {
        return url;
    }

    public void setUrl(@Nullable String url) {
        this.url = url;
    }

    public @Nullable String getUsername() {
        return username;
    }

    public void setUsername(@Nullable String username) {
        this.username = username;
    }

    public @Nullable String getPassword() {
        return password;
    }

    public void setPassword(@Nullable String password) {
        this.password = password;
    }

    public @Nullable String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(@Nullable String driverClassName) {
        this.driverClassName = driverClassName;
    }
}

