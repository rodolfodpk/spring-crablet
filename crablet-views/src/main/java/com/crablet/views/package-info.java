/**
 * View projections for materialized read models.
 * <p>
 * This module provides asynchronous view projections using the generic event processor
 * infrastructure. Views subscribe to events by type and/or tags, project them into
 * relational tables, and track progress independently per view.
 * <p>
 * <strong>Key Components:</strong>
 * <ul>
 *   <li>{@link com.crablet.views.ViewProjector} - Interface for view projectors</li>
 *   <li>{@link com.crablet.views.AbstractViewProjector} - Base class for view projectors (non-generic)</li>
 *   <li>{@link com.crablet.views.AbstractTypedViewProjector} - Base class for typed view projectors with sealed interfaces</li>
 *   <li>{@link com.crablet.views.config.ViewSubscriptionConfig} - Configuration for event subscriptions</li>
 *   <li>{@link com.crablet.views.config.ViewsConfig} - Global views configuration</li>
 *   <li>{@link com.crablet.views.service.ViewManagementService} - Unified service for view management and detailed progress monitoring</li>
 * </ul>
 * <p>
 * <strong>How It Works:</strong>
 * <ol>
 *   <li>Users create view tables (e.g., wallet_views) using Flyway migrations</li>
 *   <li>Users implement ViewProjector (recommended: extend {@link com.crablet.views.AbstractTypedViewProjector} for sealed interfaces)
 *       to project events into view tables (using JdbcTemplate, Spring Data JDBC, JOOQ, or any database access technology)</li>
 *   <li>Users configure ViewSubscriptionConfig to subscribe to specific event types/tags
 *       (tags are stored as "key=value" format in PostgreSQL)</li>
 *   <li>The generic EventProcessor polls events and calls projectors asynchronously</li>
 *   <li>Progress is tracked independently per view in view_progress table</li>
 * </ol>
 * <p>
 * <strong>Base Classes:</strong>
 * The module provides base classes to reduce boilerplate:
 * <ul>
 *   <li><strong>{@link com.crablet.views.AbstractTypedViewProjector}</strong> - For sealed interfaces (recommended):
 *       Provides transaction support, automatic deserialization, error handling, and type-safe pattern matching</li>
 *   <li><strong>{@link com.crablet.views.AbstractViewProjector}</strong> - For non-typed usage:
 *       Provides transaction support and error handling without type constraints</li>
 * </ul>
 * Both base classes automatically wrap each batch of events in a transaction for atomicity.
 * <p>
 * <strong>Database Access:</strong>
 * View projectors can use any database access technology:
 * <ul>
 *   <li><strong>JdbcTemplate</strong> (recommended default) - Simple, direct SQL control, excellent for PostgreSQL-specific features like ON CONFLICT</li>
 *   <li><strong>Spring Data JDBC</strong> - Repository abstraction for simpler CRUD operations</li>
 *   <li><strong>JOOQ</strong> - Type-safe SQL builder (requires code generation setup)</li>
 *   <li><strong>Plain JDBC</strong> - Maximum control for complex scenarios</li>
 * </ul>
 * <p>
 * <strong>Idempotency:</strong>
 * View projectors MUST use idempotent operations (SQL ON CONFLICT, upserts, or equivalent)
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

