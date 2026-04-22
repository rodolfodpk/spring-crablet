# Crablet Template Repo Onboarding Plan

## Summary

Create a first-user experience where teams clone a Crablet application template and develop one
vertical slice at a time with Claude Code, a bundled event-modeling workflow, and the
`embabel-codegen` MCP tools.

The intended user journey is:

```text
clone template repo
  -> open Claude Code
  -> describe one vertical slice
  -> update event-model.yaml
  -> run embabel_plan
  -> approve the planned artifacts
  -> run embabel_generate
  -> run ./mvnw verify
```

Users should not need to start inside `spring-crablet` or manually discover the generator JAR.
The framework repository remains the place where the runtime and generator are developed. The
template repository is the place where application teams start.

## Product Decision

The primary onboarding artifact should be a Crablet app template, not a command that users run
from the framework repository.

`embabel-codegen init` remains useful for creating or patching apps, but the lowest-friction
path should be:

```bash
git clone <crablet-template-repo> my-service
cd my-service
claude
```

The template should already contain the app skeleton, Claude instructions, MCP configuration, and
starter event model.

## User Prerequisites

The user needs:

- Java 25
- Maven wrapper from the template repo
- PostgreSQL
- Claude Code
- `ANTHROPIC_API_KEY` in the shell that starts Claude Code

Optional but useful:

- Docker or local Postgres helper scripts for first-run setup
- GitHub CLI for creating pull requests from generated slices

## Template Repository Shape

Recommended layout:

```text
my-crablet-app/
├── CLAUDE.md
├── README.md
├── event-model.yaml
├── pom.xml
├── mvnw
├── .claude/
│   ├── settings.json
│   └── skills/
│       └── event-modeling/
│           └── SKILL.md
├── tools/
│   └── embabel-codegen.jar
├── src/main/java/com/example/app/Application.java
└── src/main/resources/
    ├── application.yml
    └── db/migration/
        └── V1__eventstore_schema.sql
```

The template may initially vendor `tools/embabel-codegen.jar`. Longer term, this should move to a
versioned release artifact or Maven plugin to avoid committing generated binaries.

## Template CLAUDE.md Contract

The template `CLAUDE.md` should be short and application-focused. It should tell Claude:

1. This is a Crablet application.
2. Add features one vertical slice at a time.
3. Update `event-model.yaml` before editing generated Java.
4. Ask for missing business facts before changing files.
5. Run `embabel_plan` and show the planned artifacts before `embabel_generate`.
6. Prefer improving the model over hand-patching structural generated code.
7. Run `./mvnw verify` after generation.

Example instruction:

```text
When adding a feature, first model the command, event, tags, command pattern, validation,
read model, automation, and outbox requirements. Do not generate code until embabel_plan
has been reviewed.
```

## Claude Tools

The template should expose these MCP tools through `.claude/settings.json`:

- `embabel_plan`
- `embabel_generate`

`embabel_init` is optional in a template repo because the app is already initialized. It remains
useful for creating a second service.

Local template configuration:

```json
{
  "mcpServers": {
    "embabel-codegen": {
      "command": "java",
      "args": [
        "-jar",
        "./tools/embabel-codegen.jar",
        "--mcp"
      ]
    }
  }
}
```

During framework development, a template can point at a sibling checkout instead:

```json
{
  "mcpServers": {
    "embabel-codegen": {
      "command": "java",
      "args": [
        "-jar",
        "../spring-crablet/embabel-codegen/target/embabel-codegen.jar",
        "--mcp"
      ]
    }
  }
}
```

## Template Make Targets

Recommended user-facing targets:

```makefile
plan:
	java -jar tools/embabel-codegen.jar plan --model event-model.yaml

generate:
	java -jar tools/embabel-codegen.jar generate --model event-model.yaml --output src/main/java

verify:
	./mvnw verify
```

Recommended contributor targets:

```makefile
check: verify
	java -jar tools/embabel-codegen.jar plan --model event-model.yaml
```

If the template does not vendor the JAR, these targets should use the release download path or
document the required environment variable, for example `CRABLET_CODEGEN_JAR`.

## First Vertical Slice Flow

The user starts with an outcome:

```text
Add the first vertical slice: Submit Loan Application.

Outcome:
- a customer submits a loan application
- Crablet records LoanApplicationSubmitted
- reviewers can query pending applications

Use the Crablet feature-slice workflow.
Ask for missing facts before changing files.
```

Claude asks for missing facts:

- What uniquely identifies the application?
- Is submission idempotent?
- Which fields are required?
- Which fields should reviewers see?
- Does this slice need automation or outbox publishing?

Claude updates `event-model.yaml`, then runs:

```text
embabel_plan model=event-model.yaml
```

The user reviews the planned artifacts. Only after confirmation should Claude run:

```text
embabel_generate model=event-model.yaml output=src/main/java
```

Then:

```bash
./mvnw verify
```

## Event-Modeling Skill

The template should include an `event-modeling` skill or equivalent agent instructions.

The skill should:

- produce or update `event-model.yaml`
- keep changes scoped to one slice
- ask clarifying questions before writing the model
- validate against `EVENT_MODEL_FORMAT.md`
- avoid inventing adapters, credentials, or unsupported business rules
- recommend `embabel_plan` before generation

This is currently the biggest missing packaging piece. The framework repo references the
event-modeling workflow, but the template must ship the actual skill/instructions that make the
workflow repeatable for app teams.

## Versioning And Distribution

Phase 1:

- Keep `embabel-codegen` built from the framework repo.
- Use a sibling checkout or manually copied `tools/embabel-codegen.jar`.
- Validate the template with local users.

Phase 2:

- Publish `embabel-codegen` as a release artifact.
- Update template setup to download or reference a versioned generator.
- Add a template upgrade guide for generator/runtime version alignment.

Phase 3:

- Consider a Maven plugin or wrapper command.
- Add CI checks that run `plan` and `./mvnw verify` against the template fixture.

## Work Breakdown

1. Create a template repository or `templates/crablet-app/` directory.
2. Add Spring Boot + Crablet skeleton.
3. Add starter `event-model.yaml`.
4. Add `CLAUDE.md` with slice-first instructions.
5. Add `.claude/settings.json` for `embabel-codegen`.
6. Add the event-modeling skill/instructions.
7. Add Make targets: `plan`, `generate`, `verify`, `check`.
8. Add a README showing the first 10 minutes of use.
9. Test the full Submit Loan Application slice from a fresh clone.
10. Decide how the template gets the generator JAR for non-framework users.

## Acceptance Criteria

A new user can:

1. Clone the template.
2. Set `ANTHROPIC_API_KEY`.
3. Open Claude Code.
4. Describe one vertical slice.
5. Review `event-model.yaml`.
6. Run `embabel_plan`.
7. Run `embabel_generate`.
8. Run `./mvnw verify`.

The user should not need to:

- manually copy runtime dependencies
- edit `.claude/settings.json`
- understand the internals of `spring-crablet`
- generate an entire app before modeling one feature slice

## Open Questions

- Should the template live in this repository under `templates/`, or as a separate GitHub template
  repository?
- Should the first version vendor `embabel-codegen.jar`, download it from releases, or require a
  sibling `spring-crablet` checkout?
- Should `event-modeling` be a Claude skill, an agent prompt, or both?
- How much generated code should the template commit in its starter state?
- Should generated files be overwritten, merged, or written into a generated source package with a
  customization boundary?
