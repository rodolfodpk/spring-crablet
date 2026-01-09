/**
 * Leader election for distributed outbox processing.
 * <p>
 * This package previously provided leader election functionality for the outbox.
 * Leader election has been moved to the generic
 * {@link com.crablet.eventprocessor.leader.LeaderElector} in the
 * {@code crablet-event-processor} module.
 * <p>
 * <strong>Current Architecture:</strong>
 * Leader election is now provided by the generic processor infrastructure:
 * <ul>
 *   <li>{@link com.crablet.eventprocessor.leader.LeaderElector} - Generic leader election interface</li>
 *   <li>{@link com.crablet.eventprocessor.leader.LeaderElectorImpl} - Implementation using PostgreSQL advisory locks</li>
 * </ul>
 * <p>
 * <strong>Leader Election Strategy:</strong>
 * The generic processor supports per-processor leader election:
 * <ul>
 *   <li>Each processor (topic, publisher) pair can have its own leader</li>
 *   <li>PostgreSQL advisory locks provide distributed coordination without external dependencies</li>
 *   <li>Followers periodically retry acquiring the lock to detect leader crashes</li>
 * </ul>
 * <p>
 * <strong>How It Works:</strong>
 * <ol>
 *   <li>On startup, each instance attempts to acquire advisory locks for processors</li>
 *   <li>The instance that acquires a lock becomes the leader for that processor</li>
 *   <li>The leader processes events for that processor</li>
 *   <li>Followers periodically retry lock acquisition (configurable interval)</li>
 *   <li>If the leader crashes, the lock is automatically released, allowing a follower to become leader</li>
 * </ol>
 * <p>
 * <strong>Spring Integration:</strong>
 * Leader election is automatically configured via {@link com.crablet.outbox.config.OutboxAutoConfiguration}
 * when {@code crablet.outbox.enabled=true}. No manual bean configuration is required.
 *
 * @see com.crablet.eventprocessor.leader.LeaderElector
 * @see com.crablet.eventprocessor.leader.LeaderElectorImpl
 * @see com.crablet.outbox.config.OutboxAutoConfiguration
 */
package com.crablet.outbox.leader;

