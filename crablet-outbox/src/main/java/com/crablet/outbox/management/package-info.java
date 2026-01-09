/**
 * Outbox management operations and REST API.
 * <p>
 * This package previously provided management functionality for monitoring and controlling
 * outbox operations. The management functionality has been moved to the generic
 * {@link com.crablet.eventprocessor.management.ProcessorManagementService} and
 * {@link com.crablet.eventprocessor.management.ProcessorManagementController} in the
 * {@code crablet-event-processor} module.
 * <p>
 * <strong>Current Architecture:</strong>
 * Management operations are now provided by the generic processor infrastructure:
 * <ul>
 *   <li>{@link com.crablet.eventprocessor.management.ProcessorManagementService} - Generic management service</li>
 *   <li>{@link com.crablet.eventprocessor.management.ProcessorManagementController} - Generic REST API</li>
 * </ul>
 * <p>
 * <strong>Management Operations:</strong>
 * <ul>
 *   <li>Pause/resume processors</li>
 *   <li>Reset failed processors</li>
 *   <li>Get processor status and health</li>
 *   <li>Monitor processor lag (how far behind the latest event)</li>
 *   <li>View backoff information</li>
 * </ul>
 * <p>
 * <strong>REST API:</strong>
 * The generic management controller provides REST endpoints for:
 * <ul>
 *   <li>{@code GET /api/processors} - List all processors with status</li>
 *   <li>{@code GET /api/processors/{id}} - Get specific processor status</li>
 *   <li>{@code POST /api/processors/{id}/pause} - Pause a processor</li>
 *   <li>{@code POST /api/processors/{id}/resume} - Resume a processor</li>
 *   <li>{@code POST /api/processors/{id}/reset} - Reset a failed processor</li>
 * </ul>
 * <p>
 * <strong>Usage:</strong>
 * The management service and controller are automatically available when using the
 * generic event processor. No additional configuration is required.
 *
 * @see com.crablet.eventprocessor.management.ProcessorManagementService
 * @see com.crablet.eventprocessor.management.ProcessorManagementController
 */
package com.crablet.outbox.management;

