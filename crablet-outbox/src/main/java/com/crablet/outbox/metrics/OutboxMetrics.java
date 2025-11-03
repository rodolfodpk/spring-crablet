package com.crablet.outbox.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Metrics collection for Outbox operations.
 * Users must define as @Bean in Spring configuration.
 */
public class OutboxMetrics implements MeterBinder {
    
    private final String instanceId;
    
    // Counters
    private Counter eventsPublishedTotal;
    private Counter processingCyclesTotal;
    private Counter errorsTotal;
    private Counter autoPauseTotal;
    
    // Gauges
    private final AtomicInteger isLeader = new AtomicInteger(0);
    private final Map<String, AtomicInteger> publisherLeadership = new ConcurrentHashMap<>();
    private MeterRegistry registry;
    
    public OutboxMetrics(Environment environment) {
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
    
    @Override
    public void bindTo(MeterRegistry registry) {
        this.registry = registry;
        
        // Counters
        eventsPublishedTotal = Counter.builder("outbox.events.published")
            .description("Total number of events published")
            .register(registry);
        
        processingCyclesTotal = Counter.builder("outbox.processing.cycles")
            .description("Total number of processing cycles")
            .register(registry);
        
        errorsTotal = Counter.builder("outbox.errors")
            .description("Total number of publishing errors")
            .register(registry);
        
        autoPauseTotal = Counter.builder("outbox.auto_pause_total")
            .description("Total number of auto-pause events due to retry exhaustion")
            .register(registry);
        
        // Gauges - Leadership with instance tag
        Gauge.builder("outbox.is_leader", isLeader, AtomicInteger::get)
            .description("Whether this instance is the outbox leader (1=leader, 0=follower)")
            .tag("instance", instanceId)
            .register(registry);
    }
    
    public void recordEventsPublished(String publisherName, int count) {
        if (eventsPublishedTotal != null) {
            eventsPublishedTotal.increment(count);
        }
    }
    
    public void recordProcessingCycle() {
        if (processingCyclesTotal != null) {
            processingCyclesTotal.increment();
        }
    }
    
    public void recordError(String publisherName) {
        if (errorsTotal != null) {
            errorsTotal.increment();
        }
    }
    
    public void recordAutoPause(String topic, String publisher, int errorCount, String lastError) {
        if (registry != null) {
            Counter.builder("outbox.auto_pause_total")
                .tag("topic", topic)
                .tag("publisher", publisher)
                .register(registry)
                .increment();
        }
    }
    
    public void setLeader(boolean leader) {
        isLeader.set(leader ? 1 : 0);
    }
    
    public void setLeaderForPublisher(String publisherName, boolean leader) {
        AtomicInteger leaderGauge = publisherLeadership.computeIfAbsent(publisherName, name -> {
            AtomicInteger gauge = new AtomicInteger(0);
            if (registry != null) {
                Gauge.builder("outbox.is_leader", gauge, AtomicInteger::get)
                    .description("Whether this instance is leader for specific publisher")
                    .tag("publisher", name)
                    .tag("instance", instanceId)
                    .register(registry);
            }
            return gauge;
        });
        leaderGauge.set(leader ? 1 : 0);
    }
}
