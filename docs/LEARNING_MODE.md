# Learning Mode

The recommended way to learn Crablet is to run **one application instance** with commands and poller-backed modules together.

That means:

- `crablet-eventstore`
- `crablet-commands`
- `crablet-views`
- `crablet-automations`
- `crablet-outbox`

## Why This Is The Default Learning Setup

- You see the full write path and read path in one place.
- You avoid introducing cluster topology before the model is clear.
- You can observe asynchronous projections and side effects without deployment noise.
- It matches the simplest correctness-first production topology for poller-backed modules.

## What To Expect

In this mode, one application instance handles:

- synchronous command execution
- event appends
- asynchronous view processing
- asynchronous automation processing
- asynchronous outbox publishing

This is not just a demo trick. It is the simplest way to understand the framework.

## Recommended Flow

1. Start the wallet example app.
2. Execute a write command such as open wallet or deposit.
3. Query the read model.
4. Inspect view and automation management endpoints.
5. Only after that, read the deeper DCB and topology docs.

## Important Boundary

Learning mode is the recommended default when any poller-backed module is enabled.

If your application includes `crablet-views`, `crablet-outbox`, or `crablet-automations`, keep the deployment model simple first and default to one instance per cluster.

For production guidance, see [DEPLOYMENT_TOPOLOGY.md](DEPLOYMENT_TOPOLOGY.md).
