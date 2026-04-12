# Commands-First Adoption

Crablet should be easy to adopt from the command side first.

This is the recommended first production path when you want:

- event-sourced writes
- explicit concurrency rules
- Spring-friendly command execution
- no poller-backed modules yet

## Start With These Modules

- `crablet-eventstore`
- `crablet-commands`

Treat `views`, `automations`, and `outbox` as later add-ons, not part of the first integration milestone.

## Why This Path Matters

- It proves the core payoff quickly.
- It keeps infrastructure and deployment simple.
- It avoids introducing the event poller until the command model is stable.
- It is the natural path for a future command-side starter.

## Recommended First Milestone

1. Define events and tags.
2. Create one command interface.
3. Implement one handler for an idempotent or non-commutative command.
4. Execute it through `CommandExecutor`.
5. Verify the resulting events directly from the event store.

## What A Starter Should Cover

A command-side starter should configure:

- `EventStore`
- `CommandExecutor`
- handler discovery
- transaction wiring

It should not promise:

- views
- automations
- outbox
- event-poller lifecycle management

Those modules change the deployment model and should stay explicit.

## Scaling Guidance

If you are only using the command side, application instances can scale horizontally in the normal Spring Boot way.

Once you add poller-backed modules, revisit the topology guidance in [DEPLOYMENT_TOPOLOGY.md](DEPLOYMENT_TOPOLOGY.md).
