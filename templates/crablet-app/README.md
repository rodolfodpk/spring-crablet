# Crablet App Template

This template is the intended starting point for an AI-first Crablet application.

The workflow is:

```text
describe one vertical slice
  -> update event-model.yaml
  -> run make diagram-preview
  -> run crablet_plan
  -> approve the planned artifacts
  -> run crablet_generate with output: "src/main/java"
  -> run ./mvnw verify
```

## Prerequisites

- Java 25
- PostgreSQL
- Node.js for `make diagram-preview`
- Claude Code, Cursor, Codex, or a terminal workflow
- `crablet-codegen.jar`

Phase 1 uses a local generator JAR. From a sibling `spring-crablet` checkout:

```bash
cd ../spring-crablet
make install
make codegen-build

cd ../my-crablet-app
mkdir -p tools
cp ../spring-crablet/crablet-codegen/target/crablet-codegen.jar tools/crablet-codegen.jar
```

Longer term, the template should download a versioned release artifact instead of copying from a
framework checkout.

## First Slice

Open Claude Code from your app directory (not from inside spring-crablet):

```bash
export ANTHROPIC_API_KEY=sk-ant-...   # for Claude Code; not needed for crablet generate
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

Claude should update `event-model.yaml`, run `crablet_plan`, ask you to review the planned
artifacts, then run `crablet_generate` only after the plan looks right.

Cursor can use the same MCP tools through `.cursor/mcp.json`. Codex and other agents can edit
`event-model.yaml` and use the local `make plan` / `make generate` commands.

For a detailed transcript, see
[Submit Loan Application Claude Dialogue](../../docs/user/examples/submit-loan-application-claude-dialogue.md).

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
I will update event-model.yaml, run crablet_plan, show the planned artifacts, and wait for approval
before running crablet_generate.
```

## How code generation works

The template’s **`.claude/settings.json`** and **`.cursor/mcp.json`** start the codegen JAR in MCP
mode when Claude Code or Cursor opens the project:

```json
{
  "mcpServers": {
    "crablet-codegen": {
      "command": "java",
      "args": [
        "-jar",
        "./tools/crablet-codegen.jar",
        "--mcp"
      ]
    }
  }
}
```

That exposes these MCP tools without you running `java -jar` by hand: **`crablet_plan`**,
**`crablet_generate`**, **`crablet_init`**, **`crablet_k8s`**.

**Kubernetes:** **`make k8s`** is the Makefile entry point for the same **`crablet_k8s`** MCP tool
(no model calls; writes `k8s/base` from `event-model.yaml`).

**Output directory:** **`crablet_generate`** defaults `output` to `src/main/java`, matching
`make generate`. Omit the parameter when using this template.

**Primary workflow:** describe a slice in Claude Code or Cursor → update `event-model.yaml` →
`make diagram-preview` → `crablet_plan` → review → **`crablet_generate`** → `./mvnw verify`.

**Local commands** (below) are for Codex, other agents, terminal use, scripting, and debugging —
see each command’s description.

## Local Commands

Print the artifact plan without calling a model or writing files (CI- and script-friendly):

```bash
make plan
```

Generate the structural code:

```bash
make generate
```

Same AI codegen pipeline as **`crablet_generate`** (calls the configured model, compiles, may repair
errors). Use when regenerating from the shell — not a typical deterministic CI step.

Generate a standalone Event Modeling board preview from `event-model.yaml`:

```bash
make diagram-preview
```

This writes `diagram-preview.html` from the current model. It uses the vendored renderer in
`tools/event-model-renderer.js` and requires `js-yaml`. If it is missing, run:

```bash
npm install --prefix tools --silent
```

Generate Kubernetes manifests under `k8s/base` from `event-model.yaml` (add a `deployment:` block; see the `/crablet-event-modeling` skill and `k8s/base/README-k8s.md` after generation):

```bash
make k8s
```

Build and test the app:

```bash
make verify
```

Full Maven verification (CI- and script-friendly).

Run the local verification path:

```bash
make check
```

If the generator JAR is somewhere else:

```bash
make plan CRABLET_CODEGEN_JAR=/path/to/crablet-codegen.jar
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
`outbox` entries. If automations use `ViewBackedAutomationHandler`, keep `crablet-views` on the
classpath — the views processor does not need to be enabled in the same process.

## Rules Of Thumb

- Model one vertical slice at a time.
- Update `event-model.yaml` before editing generated Java.
- Run `plan` before `generate`.
- Prefer improving the model over hand-patching structural generated code.
- Keep manual edits for behavior that the model cannot express.
