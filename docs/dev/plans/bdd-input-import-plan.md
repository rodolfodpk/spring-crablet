# Plan: Gherkin-to-Event-Model Import for Vertical Slice Tests

## Summary

Add a new input path where a Gherkin feature file can be imported into the canonical Crablet model, then used to generate both `event-model.yaml` and slice tests.

The model stays the source of truth. Gherkin is treated as a behavior-first authoring format for drafting one vertical slice in business language before the structural contract is finalized.

## Key Changes

- Extend the codegen model with an optional `scenarios` section on `EventModel` so imported behavior can live alongside events, commands, views, automations, and outbox.
- Add a Gherkin import command to `embabel-codegen` and a matching MCP entrypoint so the same import path works from the CLI and from AI tooling.
- Parse a `.feature` file into a draft slice model before generation. The importer should support one vertical slice per file in v1, with multiple scenarios allowed when they clearly belong to the same slice.
- Keep the conversion conservative:
  - infer command, event, and view names only when the scenario text makes them explicit
  - stop and ask for missing facts instead of inventing structural details
  - preserve the ability to hand-edit the resulting `event-model.yaml`
- Add a test-generation path that emits JUnit 5 integration-style slice tests under `src/test/java`, using the existing Crablet test support style rather than introducing a separate assertion framework.
- Update the AI-tooling docs and template workflow to show the new flow: Gherkin input -> review draft model -> plan -> generate code/tests -> verify.

## Docs Updates

- Update [Feature Slice Workflow](../../user/ai-tooling/FEATURE_SLICE_WORKFLOW.md) to describe the Gherkin import step, the review loop, and the missing-facts behavior.
- Update [AI-First Workflow](../../user/ai-tooling/AI_FIRST_WORKFLOW.md) to show Gherkin as an optional front door before `event-model.yaml`.
- Update [Event Model Format](../../user/ai-tooling/EVENT_MODEL_FORMAT.md) if `scenarios` becomes part of the canonical model contract.
- Update [AI Skills](../../user/ai-tooling/AI_SKILLS.md) if the feature gets a dedicated routing skill or a new codegen skill entry.
- Update the app template README and `CLAUDE.md` so new app teams see the Gherkin import flow during onboarding.
- Add or revise the loan-application example transcript so it shows Gherkin input, model review, and test generation in one concrete walkthrough.

## Test Plan

- Add parser tests for:
  - a happy-path `.feature` file
  - missing identifiers / missing required facts
  - multiple scenarios in one file
  - unsupported or ambiguous phrasing
- Add model-roundtrip tests to ensure imported scenarios are represented in `EventModel` without losing the structural fields already used by codegen.
- Add planner tests to confirm imported scenarios do not change the existing deterministic artifact plan unless explicitly mapped into commands, events, or views.
- Add generator tests for the new test artifact path, including one vertical-slice example derived from the documented loan application workflow and one failure case for an ambiguous scenario.
- Add CLI and MCP smoke tests so the import command can be exercised from both entrypoints without touching generated app code.

## Assumptions

- `cheruin` was intended to mean `Gherkin`.
- `event-model.yaml` remains canonical; Gherkin is an input aid, not a replacement for the model contract.
- Generated tests should start at the integration-slice level first.
- Existing command-handler unit test support remains unchanged and can be reused later if the imported scenarios need a smaller test target.
- The first version only needs to support a narrow, documented subset of Gherkin that maps cleanly to one vertical slice.
- The importer should fail closed on ambiguous language and return a draft model plus missing-facts prompts instead of guessing.
- The first version should focus on the loan-application slice shape already documented in the repo, so the initial parser and tests stay aligned with an existing example.
