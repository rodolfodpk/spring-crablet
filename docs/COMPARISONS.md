# Comparisons

This page frames where Crablet fits relative to common alternatives. It is intended as a practical adoption aid, not a benchmark page.

## Crablet Vs Axon

Crablet is a smaller, more explicit framework.

Choose Crablet when:

- you want a smaller API surface
- you want concurrency intent visible in handler return types
- your consistency model depends on queries across multiple streams or tags

Choose Axon when:

- you want a larger ecosystem and more built-in enterprise patterns
- your team prefers a more opinionated framework
- aggregate-centric modeling already matches your domain well

## Crablet Vs Plain Spring + JDBC

Crablet gives you an event-sourcing model and concurrency semantics instead of asking every team to reinvent them.

Choose Crablet when:

- you want event history as the source of truth
- you want a reusable command execution model
- you want explicit idempotent, commutative, and non-commutative flows

Choose plain Spring + JDBC when:

- CRUD tables are enough
- event sourcing is not buying you anything
- you do not need the additional modeling and operational complexity

## Crablet Vs EventStoreDB

These are not identical categories.

EventStoreDB is primarily an event database and ecosystem choice. Crablet is a Spring-centric framework and programming model built around PostgreSQL and DCB-style consistency.

Choose Crablet when:

- you want to stay in a Spring Boot + PostgreSQL environment
- you want framework-level command and projection guidance
- you want a smaller adoption surface inside an existing relational stack

Choose EventStoreDB when:

- the event database itself is the central platform choice
- your architecture already assumes that ecosystem and operational model

## Positioning Summary

Crablet should be presented as:

- smaller than Axon
- more structured than plain Spring + JDBC
- different from EventStoreDB because it is a framework choice, not just a database choice

The key message is simple:

Crablet is for Spring teams that want event sourcing with explicit consistency semantics, without starting from either raw infrastructure or a very large framework.
