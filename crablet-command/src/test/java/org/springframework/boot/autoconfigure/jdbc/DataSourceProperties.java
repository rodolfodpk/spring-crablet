package org.springframework.boot.autoconfigure.jdbc;

/**
 * Compatibility shim for Spring Boot 4.0.1 auto-configuration bug.
 * 
 * <p>This class provides a compatibility layer for Spring Boot 4.0.1 auto-configuration
 * that still references the removed DataSourceProperties class. This shim allows
 * the auto-configuration to work without errors.
 * 
 * <p>This is a temporary workaround until Spring Boot 4.0.2+ fixes the issue.
 */
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

