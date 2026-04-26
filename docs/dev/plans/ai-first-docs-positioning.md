# AI-First Documentation Positioning Plan

## Summary

Reposition Crablet's public documentation around an AI-assisted, event-model-first product
story. The Java runtime API remains important, but it should be presented as the substrate
under generated applications, not as the first product promise.

Current docs lead with:

- Crablet as a Java 25 event sourcing framework for Spring Boot
- `EventStore`, `CommandHandler`, and `CommandExecutor` as the first concepts
- manual command-side adoption as the default new-app path

The Embabel/codegen direction implies a different product story:

- start from an event-modeled domain
- produce a structured `event-model.yaml`
- generate events, commands, handlers, views, automations, outbox publishers, and tests
- compile and repair generated code
- run on Crablet's Java runtime
- drop down to the runtime APIs only when customization is needed

This direction makes sense because Crablet's core value is not just a smaller event sourcing API.
Its value is helping teams build DCB-style, event-sourced Spring applications without manually
assembling every structural artifact.

## Positioning Decision

Lead with:

> Crablet helps Spring teams turn an event-modeled domain into a working event-sourced
> application, using AI-assisted code generation on top of a small Java runtime.

Avoid leading with:

> Crablet is a Java 25 event sourcing framework for Spring Boot.

The second statement is still true, but it describes the implementation layer. The first
statement describes the product outcome.

## Documentation Principles

1. Put the event model before the Java API.
2. Present code generation as the primary workflow.
3. Present the runtime APIs as the manual path and customization layer.
4. Keep the claims defensible until the generator is proven.
5. Use "AI-assisted app generation" as product language; treat Embabel as an implementation
   detail in planning docs.
6. Push missing behavior back into the event model rather than normalizing TODO-heavy generated
   code.

## Root README Changes

The root `README.md` should become the product front door for the AI-first workflow.

Recommended opening:

```markdown
# Crablet: AI-Assisted Event-Sourced Spring Applications From Event Models

Crablet helps Spring teams turn an event-modeled domain into a working event-sourced
application. It uses AI-assisted generation to produce the structural code around commands,
events, views, automations, outbox publishers, and tests, then runs that code on a small Java 25
Spring Boot runtime.
```

The first value bullets should change from API-centered claims to workflow-centered claims:

- Event-model-first workflow for DCB-style domains
- Generated structural code for commands, events, views, automations, outbox, and tests
- Small Java runtime for consistency, persistence, polling, and operational behavior
- Manual APIs available when generated code needs customization
- Explicit concurrency semantics: commutative, non-commutative, and idempotent flows

The README should still link to runtime modules, but after the workflow overview.

## New Primary Guide

Add a new guide:

```text
docs/AI_FIRST_WORKFLOW.md
```

Alternative names:

- `docs/EVENT_MODEL_TO_APP.md`
- `docs/GENERATE_A_CRABLET_APP.md`

Recommended title:

```markdown
# AI-First Workflow
```

Recommended structure:

1. Model the domain with event modeling.
2. Produce `event-model.yaml`.
3. Run the code generator.
4. Compile and repair.
5. Run the generated Spring app.
6. Customize only where the model did not express business behavior.

Example flow:

```bash
/event-modeling Wallet domain
# produces event-model.yaml

java -jar embabel-codegen/target/embabel-codegen.jar generate \
  --model event-model.yaml \
  --output ../wallet-generated
```

If the generator is not production-ready yet, label the guide as product direction or preview
rather than stable usage.

## Quickstart Changes

The current `docs/QUICKSTART.md` starts with the wallet example app and curl commands. That is
still useful, but it should not be the first product experience once AI-first positioning lands.

Recommended split:

- `docs/QUICKSTART.md`: generate or inspect a working app from an event model
- `wallet-example-app/README.md`: complete reference app details

The quickstart should show the event-model-to-app path first. It can then link to the existing
wallet example for a runnable, hand-maintained reference implementation.

## Manual App Guide Changes

`docs/CREATE_A_CRABLET_APP.md` should be demoted from the default start path to the manual path.

Recommended intro:

```markdown
This guide shows the manual path for creating a Crablet application directly against the Java
runtime APIs. Use it when you want explicit control over the code, when generated code is not
appropriate, or while the AI-first generator is still maturing.
```

It should link back to `docs/AI_FIRST_WORKFLOW.md` near the top.

## Embabel Plan Rename

The existing plan:

```text
docs/dev/plans/embabel-codegen-from-event-model.md
```

uses implementation-first language. Keep the file if it is already referenced, but future docs
should use product-first naming:

```text
docs/dev/plans/ai-assisted-app-generation-from-event-models.md
```

Recommended heading:

```markdown
# AI-Assisted App Generation From Event Models
```

Embabel should be described as the initial implementation mechanism, not the product itself.

## Claim Discipline

The current Embabel plan says the goal is "zero TODOs in generated code." Keep the ambition, but
make public-facing docs more precise:

```markdown
The generator should produce compiling, structurally complete code. Missing behavior should be
captured in the event model rather than left as framework boilerplate TODOs.
```

This makes the claim testable and avoids implying that AI can infer business rules that the model
does not contain.

## Recommended Docs Navigation

Organize docs by user intent:

```text
Start Here
- AI-First Workflow
- Event Model Format
- Generate A Crablet App
- Wallet Generated Example

Runtime Concepts
- DCB And Crablet
- Command Patterns
- Views
- Automations
- Outbox

Manual API Path
- Create A New Crablet App
- Commands-First Adoption
- Tutorial

Operations
- Deployment Topology
- Management API
- Leader Election
- Observability
```

## Proposed Implementation Sequence

1. Add `docs/AI_FIRST_WORKFLOW.md`.
2. Update the root `README.md` opening, value bullets, and "Where To Go Next" links.
3. Update `docs/QUICKSTART.md` to point first at the event-model-to-app workflow.
4. Update `docs/CREATE_A_CRABLET_APP.md` to clearly label it as the manual API path.
5. Add or extract `docs/EVENT_MODEL_FORMAT.md` from the YAML section in the Embabel plan.
6. Rename or supersede the Embabel plan with product-first wording.
7. Keep module READMEs mostly API/reference-oriented, but add a short note that generated apps
   target these APIs.

## Risk And Mitigation

The main risk is over-positioning the AI-first workflow before the generator works well enough.

Mitigation:

- mark generator docs as preview until compile-and-repair quality is validated
- keep the manual API path fully documented
- use the wallet domain as the reference acceptance test
- require generated wallet code to compile and diff reasonably against the maintained example
- avoid claiming that business behavior can be inferred when it is absent from the event model

## Bottom Line

The AI-first direction makes sense. It lowers the adoption cost of DCB/event-sourced Spring apps
and gives Crablet a stronger product identity than "another Java event sourcing framework."

The docs should make the new center of gravity explicit:

> Event model first. AI-generated structure second. Crablet runtime underneath. Manual APIs when
> needed.
