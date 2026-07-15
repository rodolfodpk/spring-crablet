# Fluent StateProjector Builder

## Summary

Add an additive fluent builder to StateProjector so projection code registers typed event
transitions instead of hand-writing getEventTypes() plus string-based switch logic.

Target usage:

```java
StateProjector<SubscriptionState> projector =
    StateProjector.<SubscriptionState>builder(
            "subscription-state-projector-" + courseId + "-" + studentId,
            SubscriptionState.initial())
        .on(CourseDefined.class, (state, event) ->
            event.courseId().equals(courseId) ? state.withCourse(event.capacity()) : state)
        .on(CourseCapacityChanged.class, (state, event) ->
            event.courseId().equals(courseId) ? state.withCapacity(event.newCapacity()) : state)
        .on(StudentRegistered.class, (state, event) ->
            event.studentId().equals(studentId) ? state.withStudentExists() : state)
        .on(StudentSubscribedToCourse.class, (state, event) ->
            state.applySubscription(
                    event.courseId().equals(courseId),
                    event.studentId().equals(studentId)))
        .build();
```

## Key Changes

- Add only the explicit-id builder API:
    - `static <T> Builder<T> builder(String id, T initialState)`
    - no `builder(T initialState)` overload, to avoid ambiguous anonymous-projector ids.
- Add nested public `StateProjector.Builder<T>` and `@FunctionalInterface EventTransition<T, E>`.
- Builder behavior:
    - `.on(Class<E> eventClass, EventTransition<T, E> transition)` registers `EventType.type(eventClass)`.
    - Duplicate registrations throw `IllegalArgumentException` immediately in `on()`.
    - Calling `on()` after `build()` throws `IllegalStateException` — a boolean `built` flag is
      set in `build()` and checked at the top of `on()`. Prevents undefined behavior if a builder
      reference is retained and mutated after the projector is constructed.
    - `build()` returns a normal `StateProjector<T>` with stable `getId()`, ordered
      `getEventTypes()`, and internal dispatch/deserialization.
    - Unregistered events return the current state defensively.
    - Internal erasure uses one localized unchecked cast with `@SuppressWarnings("unchecked")`,
      justified because `on()` stores the event class and transition atomically.

## Example Refactor

- Refactor `SubscribeStudentToCourseCommandHandler.SubscriptionStateProjector` to use the builder,
  preferably through a private factory method returning `StateProjector<SubscriptionState>`.
- Add required immutable helpers to `SubscriptionState`:
    - `static SubscriptionState initial()`
    - `withCourse(int capacity)`
    - `withCapacity(int capacity)`
    - `withStudentExists()`
    - `applySubscription(boolean affectsCourse, boolean affectsStudent)`
- Keep payload guards inside lambdas because the decision model can include events for both the
  course and the student.

## Test Plan

- Add `StateProjectorTest` coverage for:
    - builder exposes registered event types in declaration order.
    - custom id is returned by `getId()`.
    - registered handler deserializes the concrete event type and updates state.
    - unregistered event returns current state unchanged.
    - duplicate `on()` throws `IllegalArgumentException` immediately (not at `build()` time).
    - `on()` after `build()` throws `IllegalStateException`.
- Run:
    - `./mvnw -pl crablet-eventstore test`
    - `./mvnw -pl shared-examples-domain,crablet-commands test` after the course handler refactor.

## Assumptions

- This remains fluent-builder based, not annotation/reflection based.
- Existing hand-written `StateProjector` implementations stay supported unchanged.
- `Query` remains responsible for tag filtering; projectors only handle in-memory state transitions.
