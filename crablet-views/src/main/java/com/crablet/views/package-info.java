/**
 * View projections for materialized read models.
 * <p>
 * This module provides asynchronous view projections using the generic event processor
 * infrastructure. Views subscribe to events by type and/or tags, project them into
 * relational tables using JOOQ, and track progress independently per view.
 * <p>
 * <strong>Key Components:</strong>
 * <ul>
 *   <li>{@link com.crablet.views.ViewProjector} - Interface for view projectors</li>
 *   <li>{@link com.crablet.views.config.ViewSubscriptionConfig} - Configuration for event subscriptions</li>
 *   <li>{@link com.crablet.views.config.ViewsConfig} - Global views configuration</li>
 *   <li>{@link com.crablet.views.controller.ViewController} - REST API for view management</li>
 * </ul>
 * <p>
 * <strong>How It Works:</strong>
 * <ol>
 *   <li>Users create view tables (e.g., wallet_views) using Flyway migrations</li>
 *   <li>Users implement ViewProjector to project events into view tables using JOOQ</li>
 *   <li>Users configure ViewSubscriptionConfig to subscribe to specific event types/tags
 *       (tags are stored as "key=value" format in PostgreSQL)</li>
 *   <li>The generic EventProcessor polls events and calls projectors asynchronously</li>
 *   <li>Progress is tracked independently per view in view_progress table</li>
 * </ol>
 * <p>
 * <strong>Idempotency:</strong>
 * View projectors MUST use idempotent operations (JOOQ store() or SQL ON CONFLICT)
 * since events may be processed multiple times due to at-least-once semantics.
 * <p>
 * <strong>Spring Integration:</strong>
 * Enable views by setting {@code crablet.views.enabled=true} in application properties.
 * The module auto-configures all necessary beans via {@link com.crablet.views.config.ViewsAutoConfiguration}.
 *
 * @see com.crablet.eventprocessor.processor.EventProcessor
 * @see com.crablet.views.config.ViewsAutoConfiguration
 */
package com.crablet.views;

