package com.crablet.outbox;

import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Provides instance ID for outbox components.
 * Used for leader election and publisher registration.
 * <p>
 * Instance ID resolution order:
 * <ol>
 *   <li>HOSTNAME environment variable (Kubernetes pod name)</li>
 *   <li>crablet.instance.id configuration property</li>
 *   <li>Hostname (fallback)</li>
 * </ol>
 */
public class InstanceIdProvider {
    
    private final String instanceId;
    
    public InstanceIdProvider(Environment environment) {
        this.instanceId = getInstanceId(environment);
    }
    
    private String getInstanceId(Environment environment) {
        // Try Kubernetes pod name (HOSTNAME env var)
        String podName = environment.getProperty("HOSTNAME");
        if (podName != null && !podName.isEmpty()) {
            return podName;
        }
        
        // Try custom instance ID from config
        String customId = environment.getProperty("crablet.instance.id");
        if (customId != null && !customId.isEmpty()) {
            return customId;
        }
        
        // Fall back to hostname
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-" + System.currentTimeMillis();
        }
    }
    
    public String getInstanceId() {
        return instanceId;
    }
}

