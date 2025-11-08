/**
 * Leader election for distributed outbox processing.
 * <p>
 * This package provides leader election functionality using PostgreSQL advisory locks,
 * ensuring only one instance processes outbox publishers at a time in distributed deployments.
 * <p>
 * <strong>Key Components:</strong>
 * <ul>
 *   <li>{@link com.crablet.outbox.leader.OutboxLeaderElector} - Manages leader election using PostgreSQL advisory locks</li>
 * </ul>
 * <p>
 * <strong>Leader Election Strategy:</strong>
 * The outbox uses a GLOBAL lock strategy:
 * <ul>
 *   <li>One instance is the leader and processes all publishers</li>
 *   <li>Followers periodically retry acquiring the lock to detect leader crashes</li>
 *   <li>PostgreSQL advisory locks provide distributed coordination without external dependencies</li>
 * </ul>
 * <p>
 * <strong>How It Works:</strong>
 * <ol>
 *   <li>On startup, each instance attempts to acquire a global advisory lock</li>
 *   <li>The instance that acquires the lock becomes the leader</li>
 *   <li>The leader processes all configured publishers</li>
 *   <li>Followers periodically retry lock acquisition (configurable interval)</li>
 *   <li>If the leader crashes, the lock is automatically released, allowing a follower to become leader</li>
 * </ol>
 * <p>
 * <strong>Heartbeat and Stale Leader Detection:</strong>
 * The leader elector tracks heartbeats and detects stale leaders automatically.
 * If a leader becomes unresponsive, followers can acquire the lock and take over.
 * <p>
 * <strong>Spring Integration:</strong>
 * Users must define OutboxLeaderElector as a Spring bean:
 * <pre>{@code
 * @Bean
 * public OutboxLeaderElector outboxLeaderElector(
 *         JdbcTemplate jdbcTemplate,
 *         OutboxConfig config,
 *         InstanceIdProvider instanceIdProvider,
 *         ApplicationEventPublisher eventPublisher) {
 *     return new OutboxLeaderElector(jdbcTemplate, config, instanceIdProvider, eventPublisher);
 * }
 * }</pre>
 *
 * @see com.crablet.outbox.processor.OutboxProcessorImpl
 */
package com.crablet.outbox.leader;

