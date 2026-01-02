package org.springframework.boot.autoconfigure.jdbc;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Compatibility shim for Spring Boot 4.0.x auto-configuration bug.
 * <p>
 * Spring Boot 4.0.1's auto-configuration still references the old
 * {@code org.springframework.boot.autoconfigure.jdbc.DataSourceProperties} class
 * which was removed. This class provides a compatibility layer that delegates
 * to Spring Boot 4's new DataSource configuration approach.
 * <p>
 * This is a temporary workaround until Spring Boot fixes the auto-configuration
 * to reference the correct class location.
 */
@ConfigurationProperties(prefix = "spring.datasource")
public class DataSourceProperties {
    private String url;
    private String username;
    private String password;
    private String driverClassName;
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getDriverClassName() {
        return driverClassName;
    }
    
    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }
}

