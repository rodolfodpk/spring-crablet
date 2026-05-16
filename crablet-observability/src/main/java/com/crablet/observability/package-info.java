/**
 * Shared observation names and low-cardinality tag conventions for Crablet modules.
 *
 * <p>This module deliberately does not depend on Crablet runtime modules, Micrometer registries,
 * or OpenTelemetry SDK types. Runtime modules own their own instrumentation and export is handled
 * by Spring Boot's observability stack.
 */
@org.jspecify.annotations.NullMarked
package com.crablet.observability;
