# Crablet App Template

This template is the intended starting point for an AI-first Crablet application.

The workflow is:

```text
describe one vertical slice
  -> update event-model.yaml
  -> run embabel_plan
  -> approve the planned artifacts
  -> run embabel_generate
  -> run ./mvnw verify
```

## Prerequisites

- Java 25
- PostgreSQL
- Claude Code
- `ANTHROPIC_API_KEY` in the shell that starts Claude Code
- `embabel-codegen.jar`

Phase 1 uses a local generator JAR. From a sibling `spring-crablet` checkout:

```bash
cd ../spring-crablet
make install
make codegen-build

cd ../my-crablet-app
mkdir -p tools
cp ../spring-crablet/embabel-codegen/target/embabel-codegen.jar tools/embabel-codegen.jar
```

Longer term, the template should download a versioned release artifact instead of copying from a
framework checkout.

## First Slice

Open Claude Code from the template root:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
claude
```

Start with one outcome:

```text
Add the first vertical slice: Submit Loan Application.

Outcome:
- a customer submits a loan application
- Crablet records LoanApplicationSubmitted
- reviewers can query pending applications

Use the Crablet feature-slice workflow.
Ask for missing facts before changing files.
```

Claude should update `event-model.yaml`, run `embabel_plan`, ask you to review the planned
artifacts, then run `embabel_generate` only after the plan looks right.

For a detailed transcript, see
[Submit Loan Application Claude Dialogue](../../docs/examples/submit-loan-application-claude-dialogue.md).

The interaction should feel like this:

```text
Claude:
I need a few business facts before changing files:

1. What uniquely identifies a loan application?
2. Should submitting the same application twice be idempotent, rejected, or treated as a new
   submission?
3. Which fields are required when the customer submits the application?
4. Which fields should reviewers see in the pending applications view?
5. Does this slice need automation or external publishing?

User:
applicationId identifies the application. Submission is idempotent.
Required fields are applicationId, customerId, requestedAmount, and purpose.
requestedAmount must be positive. IDs and purpose must not be blank.
Reviewers should see applicationId, customerId, requestedAmount, purpose, status, and submittedAt.
No automation or outbox yet.

Claude:
I will update event-model.yaml, run embabel_plan, show the planned artifacts, and wait for approval
before running embabel_generate.
```

## Local Commands

Print the artifact plan without calling Anthropic or writing files:

```bash
make plan
```

Generate the structural code:

```bash
make generate
```

Build and test the app:

```bash
make verify
```

Run the local verification path:

```bash
make check
```

If the generator JAR is somewhere else:

```bash
make plan CRABLET_CODEGEN_JAR=/path/to/embabel-codegen.jar
```

## Runtime Setup

The default datasource is:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/crablet_app
spring.datasource.username=postgres
spring.datasource.password=postgres
```

Create the database before running the app:

```bash
createdb crablet_app
```

Then:

```bash
./mvnw spring-boot:run
```

The starter POM includes the command, command-web, event store, and views modules. Add
`crablet-automations` or `crablet-outbox` when `event-model.yaml` starts using `automations` or
`outbox` entries.

## Rules Of Thumb

- Model one vertical slice at a time.
- Update `event-model.yaml` before editing generated Java.
- Run `plan` before `generate`.
- Prefer improving the model over hand-patching structural generated code.
- Keep manual edits for behavior that the model cannot express.
