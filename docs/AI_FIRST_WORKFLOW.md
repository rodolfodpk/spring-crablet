# AI-First Workflow

Crablet's product direction is to start from an event-modeled domain and generate the structural
Spring application code around it. The Java runtime APIs remain available, but they are the
substrate for generated applications and the manual path for teams that want direct control.

This workflow is currently a preview direction while the generator matures.

For application teams, the intended first-user path is to clone the
[Crablet app template](../templates/crablet-app/README.md), open Claude Code, and add one
vertical slice at a time.

## At A Glance

### Generation Flow

![Crablet AI-first workflow](assets/crablet-ai-first-high-level.png)

### Crablet Model DFD

![Crablet model DFD](assets/crablet-model-dfd.png)

## Workflow

1. Build the Crablet runtime and `embabel-codegen` tool.
2. Optionally initialize a new Spring Boot application.
3. Model one feature slice at a time.
4. Produce or update `event-model.yaml` using the [Event Model Format](EVENT_MODEL_FORMAT.md).
5. Generate the Spring application code.
6. Compile the generated app.
7. Repair generation issues until the app builds.
8. Run the app on the Crablet runtime.
9. Update the event model when missing behavior is structural, and customize code when behavior is
   genuinely application-specific.

The intended shape is:

```bash
make install
make codegen-build

java -jar embabel-codegen/target/embabel-codegen.jar init \
  --name wallet-service \
  --package com.example.wallet \
  --dir ../wallet-service

# Use a dialogue or event-modeling skill to produce event-model.yaml.

java -jar embabel-codegen/target/embabel-codegen.jar generate \
  --model event-model.yaml \
  --output ../wallet-service/src/main/java

cd ../wallet-service
./mvnw verify
```

The generator should produce compiling, structurally complete code. Missing behavior should be
captured in the event model rather than left as framework boilerplate TODOs.

For the recommended developer dialogue around a single feature, see
[Feature Slice Workflow](FEATURE_SLICE_WORKFLOW.md). For a concrete generated-slice input, see
[loan-submit-feature-slice-event-model.yaml](examples/loan-submit-feature-slice-event-model.yaml).

## Tool Entrypoints

`embabel-codegen` is built as a fat JAR:

```bash
make codegen-build
java -jar embabel-codegen/target/embabel-codegen.jar help
```

The CLI commands are:

- `init`: bootstrap a Spring Boot app with Crablet dependencies
- `plan`: print planned artifacts without calling Anthropic or writing files
- `generate`: read `event-model.yaml`, generate code, and run the compile-and-repair loop

For the documented loan-slice fixture, contributors can run the local planner smoke check:

```bash
make codegen-plan-example
```

After changing `embabel-codegen`, the event model format, or the documented fixture, run:

```bash
make codegen-check
```

Claude Code can use the same tool through MCP when `.claude/settings.json` is active:

- `embabel_init`
- `embabel_plan`
- `embabel_generate`

## What The Model Should Drive

A sufficiently rich event model should drive:

- event records and sealed event interfaces
- command records and validation
- command handlers and DCB append decisions
- state projectors used by command decisions
- materialized view projectors and SQL migrations
- automations that react to events and emit commands
- outbox publishers for integration events
- focused test scaffolding from model scenarios

The model must be explicit about types, tags, command patterns, validations, views, automation
conditions, and external adapters. The generator should fail early when the model is ambiguous
instead of guessing.

For the current preview contract, see [Event Model Format](EVENT_MODEL_FORMAT.md).

## What Still Belongs Outside Generation

The generator should not pretend to infer business facts that are not in the model.

Keep these explicit:

- business rules that the model does not describe
- external system credentials and endpoints
- deployment topology and operational choices
- domain-specific integration code behind generated adapters

When generated code exposes a missing rule, prefer improving `event-model.yaml` over editing around
structural gaps by hand.

## Runtime Relationship

Generated applications target the same runtime modules documented elsewhere in this repository:

- [Event Store](../crablet-eventstore/README.md)
- [Commands](../crablet-commands/README.md)
- [Views](../crablet-views/README.md)
- [Automations](../crablet-automations/README.md)
- [Outbox](../crablet-outbox/README.md)
- [Command Web API](../crablet-commands-web/README.md)
- [Micrometer metrics](../crablet-metrics-micrometer/README.md)

These APIs are still useful when generated code needs customization or when you choose the manual
runtime path.

## Current Manual Path

Until the generator is ready for primary use, the stable path is still:

- run the wallet reference app with [Quickstart](QUICKSTART.md)
- build a new app manually with [Create A New Crablet App Manually](CREATE_A_CRABLET_APP.md)
- learn the runtime concepts through the [Tutorial](TUTORIAL.md)

The manual path should remain fully documented. The AI-first path changes the product center of
gravity, but it does not remove the need for clear runtime references.
