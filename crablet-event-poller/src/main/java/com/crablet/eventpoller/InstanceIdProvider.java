package com.crablet.eventpoller;

import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * Provides instance ID for event processors.
 * Used for leader election and processor registration.
 * <p>
 * Instance ID resolution order:
 * <ol>
 *   <li>HOSTNAME environment variable (Kubernetes pod name)</li>
 *   <li>crablet.instance.id configuration property</li>
 *   <li>Hostname (fallback)</li>
 * </ol>
 * <p>
 * Register as a Spring bean in your application:
 * <pre>{@code
 * @Bean
 * public InstanceIdProvider instanceIdProvider(Environment environment) {
 *     return new InstanceIdProvider(environment);
 * }
 * }</pre>
 */
public class InstanceIdProvider {

    private final String instanceId;

    public InstanceIdProvider(Environment environment) {
        this.instanceId = resolve(environment);
    }

    private String resolve(Environment environment) {
        String podName = environment.getProperty("HOSTNAME");
        if (podName != null && !podName.isEmpty()) {
            return podName;
        }
        String customId = environment.getProperty("crablet.instance.id");
        if (customId != null && !customId.isEmpty()) {
            return customId;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-" + UUID.randomUUID();
        }
    }

    public String getInstanceId() {
        return instanceId;
    }
}
