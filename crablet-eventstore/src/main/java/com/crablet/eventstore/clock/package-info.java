/**
 * Clock abstraction for time-based operations.
 * <p>
 * This package provides a clock abstraction that allows for testable time control
 * and different clock implementations. The framework uses {@link com.crablet.eventstore.clock.ClockProvider}
 * for all time-based operations, enabling deterministic testing with fixed clocks.
 * <p>
 * <strong>Key Components:</strong>
 * <ul>
 *   <li>{@link com.crablet.eventstore.clock.ClockProvider} - Interface for providing timestamps</li>
 * </ul>
 * <p>
 * <strong>Usage:</strong>
 * The framework automatically uses ClockProvider for event timestamps and metric timing.
 * In tests, you can inject a test implementation that provides fixed or controllable time.
 * <p>
 * <strong>Benefits:</strong>
 * <ul>
 *   <li>Testable time-based operations</li>
 *   <li>Consistent timestamps across framework components</li>
 *   <li>Support for different clock implementations (system clock, fixed clock, etc.)</li>
 * </ul>
 *
 * @see com.crablet.eventstore.store.EventStore
 */
package com.crablet.eventstore.clock;

