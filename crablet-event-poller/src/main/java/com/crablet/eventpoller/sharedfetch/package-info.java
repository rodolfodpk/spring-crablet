/**
 * Shared-fetch extension support used by Crablet poller-backed sibling modules.
 *
 * <p>This package is a framework extension surface for modules such as views, automations, and
 * outbox. It is not intended as application-level API, but it is also not freely refactorable
 * implementation detail: changes here must be coordinated with sibling module auto-configuration.
 */
@org.jspecify.annotations.NullMarked
package com.crablet.eventpoller.sharedfetch;
