# Feature Slice Workflow

This guide describes the intended developer dialogue for adding one vertical slice with
`embabel-codegen`.

In normal app development, start from the
[Crablet app template](../templates/crablet-app/README.md) and use this workflow from inside the
cloned app.

A slice should be one observable user outcome, not a whole subsystem. For example:

- open a wallet
- deposit money
- approve a small loan automatically
- publish a notification when an application is rejected

## Prepare The Tooling

From the `spring-crablet` repository:

```bash
make install
make codegen-build
```

For generation, the shell running the tool needs:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

For a greenfield app, initialize the project:

```bash
java -jar embabel-codegen/target/embabel-codegen.jar init \
  --name loan-service \
  --package com.example.loan \
  --dir ../loan-service
```

For a brownfield app, skip `init` and point `generate --output` at the existing
`src/main/java` root.

## Practical Walkthrough

For a loan application service, start with one feature: a customer submits an application and
reviewers can see it in a pending queue.

The developer dialogue should stay outcome-oriented:

```text
I want to add the Submit Loan Application slice.

Outcome:
- a customer submits a loan application
- Crablet records LoanApplicationSubmitted
- reviewers can query pending applications

Model this as one Crablet slice.
Ask for missing facts before changing event-model.yaml.
```

The assistant should turn that into explicit model facts:

| Model fact | Example value |
|---|---|
| Command | `SubmitLoanApplication` |
| Event | `LoanApplicationSubmitted` |
| Command pattern | `idempotent` |
| Tags | `application_id`, `customer_id` |
| Required fields | `applicationId`, `customerId`, `requestedAmount`, `purpose` |
| Validation | non-blank IDs and purpose, `requestedAmount > 0` |
| Read model | `PendingLoanApplications` |

Before writing files, ask for a model review:

```text
Review the proposed event-model.yaml.
Does embabel-codegen have enough information to generate the command, event,
handler decision state, view projector, and migration without guessing?
```

Then ask for the plan:

```bash
java -jar embabel-codegen/target/embabel-codegen.jar plan \
  --model event-model.yaml
```

For this slice, the plan should name the event hierarchy, submit command, command handler,
state projector, query patterns, pending-applications projector, and Flyway migration.

Only after that plan looks right should you run `generate`. The intended loop is:

```text
model -> review -> plan -> generate -> verify -> refine the model
```

## Dialogue Shape

Use the assistant to keep the slice explicit before generating code.

```text
We are adding one Crablet vertical slice: submit a loan application.
Help me model only this slice.

Outcome:
- a customer submits an application
- the system records that fact
- reviewers can query pending applications

Ask for missing business facts before writing event-model.yaml.
```

The assistant should clarify:

- command name, fields, and validation
- event name, fields, and tags
- command pattern: `idempotent`, `commutative`, or `non-commutative`
- consistency check and guard events
- read model needed to observe the result
- automation or outbox behavior, if the slice needs one
- sample scenario used to verify the slice

For this example, the resulting model should look like
[loan-submit-feature-slice-event-model.yaml](examples/loan-submit-feature-slice-event-model.yaml).

Then ask it to update only the model:

```text
Update event-model.yaml for this slice only.
Keep existing slices intact.
Use the format in docs/EVENT_MODEL_FORMAT.md.
Do not invent external adapters or placeholder TODOs.
```

Before generation, run one model review:

```text
Review event-model.yaml against docs/EVENT_MODEL_FORMAT.md.
Tell me whether embabel-codegen has enough information to generate:
- events
- commands
- command handler decision state
- views
- automations
- outbox publishers
List missing model facts instead of guessing.
```

You can also ask `embabel-codegen` for the deterministic artifact plan without
calling Anthropic or writing files:

```bash
java -jar embabel-codegen/target/embabel-codegen.jar plan \
  --model event-model.yaml
```

From the `spring-crablet` repository, the shortest contributor smoke check for the
documented loan slice is:

```bash
make codegen-plan-example
```

After changing `embabel-codegen`, the event model format, or the documented fixture, run:

```bash
make codegen-check
```

Claude Code MCP path:

```text
Run embabel_plan with model=event-model.yaml.
```

## Generate The Slice

CLI path:

```bash
java -jar embabel-codegen/target/embabel-codegen.jar generate \
  --model event-model.yaml \
  --output ../loan-service/src/main/java
```

Claude Code MCP path:

```text
Run embabel_generate with model=event-model.yaml and output=../loan-service/src/main/java.
```

The generator runs the agent pipeline for events, commands, views, automations,
and outbox publishers, then runs a compile-and-repair loop up to three times.

## Expected Generated Artifacts

For [loan-submit-feature-slice-event-model.yaml](examples/loan-submit-feature-slice-event-model.yaml),
the generated Java should target `com.example.loan`.

Expected domain artifacts:

- `com.example.loan.domain.LoanApplicationEvent`
- `com.example.loan.domain.LoanApplicationSubmitted`

Expected command artifacts:

- `com.example.loan.command.SubmitLoanApplication`
- `com.example.loan.command.SubmitLoanApplicationCommandHandler`
- `com.example.loan.command.LoanApplicationState`
- `com.example.loan.command.LoanApplicationStateProjector`
- `com.example.loan.command.LoanApplicationQueryPatterns`

Expected view artifacts:

- `com.example.loan.view.PendingLoanApplicationsViewProjector`
- a Flyway migration named `V100__create_pending_loan_applications.sql`
- a `pending_loan_applications` table with the fields from the view model

No automation or outbox classes should be generated for this slice because the model has
empty `automations` and `outbox` arrays.

The compile-and-repair loop may adjust generated Java details, but it should not invent
new commands, events, views, automations, or publishers that are absent from the model.

## Verify The Slice

Build the target app:

```bash
cd ../loan-service
./mvnw verify
```

If the build fails because generated Java is structurally wrong, repair the generated
code or rerun generation after improving the model. If the behavior is wrong because
a business fact was missing, update `event-model.yaml` first and regenerate.

For runtime verification, use the smallest observable check:

1. Submit the command.
2. Confirm the expected event was appended.
3. Query the view, if the slice defines one.
4. Confirm automation or outbox side effects, if the slice defines them.

## Slice Completion Criteria

A generated vertical slice is complete when:

- the command has validation and an explicit command pattern
- the event has stable tags for DCB checks and projections
- the command handler has enough decision state to avoid guessing
- any view has a table shape and projector source events
- any automation has a trigger, condition, and emitted command
- any outbox publisher has handled events, topic, and adapter boundary
- `./mvnw verify` passes in the target application

Keep manual edits for application-specific behavior that the model cannot express.
Push structural omissions back into `event-model.yaml`.
