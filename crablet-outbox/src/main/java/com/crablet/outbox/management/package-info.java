/**
 * Outbox management operations and REST API.
 * <p>
 * This package provides management functionality for monitoring and controlling
 * outbox operations, including publisher status, lag monitoring, and operational controls.
 * <p>
 * <strong>Key Components:</strong>
 * <ul>
 *   <li>{@link com.crablet.outbox.management.OutboxManagementService} - Service for managing outbox operations</li>
 *   <li>{@link com.crablet.outbox.management.OutboxManagementController} - REST API for outbox management</li>
 * </ul>
 * <p>
 * <strong>Management Operations:</strong>
 * <ul>
 *   <li>Pause/resume publishers</li>
 *   <li>Reset failed publishers</li>
 *   <li>Get publisher status and health</li>
 *   <li>Monitor publisher lag (how far behind the latest event)</li>
 *   <li>View backoff information</li>
 *   <li>Get current leader instances</li>
 * </ul>
 * <p>
 * <strong>REST API:</strong>
 * The management controller provides REST endpoints for:
 * <ul>
 *   <li>{@code GET /outbox/publishers} - List all publishers with status</li>
 *   <li>{@code GET /outbox/publishers/{name}} - Get specific publisher status</li>
 *   <li>{@code POST /outbox/publishers/{name}/pause} - Pause a publisher</li>
 *   <li>{@code POST /outbox/publishers/{name}/resume} - Resume a publisher</li>
 *   <li>{@code POST /outbox/publishers/{name}/reset} - Reset a failed publisher</li>
 *   <li>{@code GET /outbox/lag} - Get publisher lag information</li>
 *   <li>{@code GET /outbox/backoff} - Get backoff information</li>
 * </ul>
 * <p>
 * <strong>Usage:</strong>
 * The management service is automatically available as a Spring service.
 * The REST controller is auto-discovered when Spring Web is on the classpath.
 * No additional configuration is required.
 *
 * @see com.crablet.outbox.processor.OutboxProcessorImpl
 */
package com.crablet.outbox.management;

