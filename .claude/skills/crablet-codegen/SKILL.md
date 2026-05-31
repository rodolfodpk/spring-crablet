---
name: crablet-codegen
description: >
  Use this skill for Crablet codegen provider setup, generator-owned vs user-owned artifact
  boundaries, repair-cycle behavior, and generation failure recovery. Do not use for feature-slice
  workflow sequencing (use crablet-app-dev), local invocation/output paths (use crablet-local-dev),
  or generator internals and adapter code (use crablet-maintainer).
---

# Crablet Codegen

This skill is a compact reference for provider configuration, artifact ownership, and recovery
when `crablet-codegen` generation fails or produces incomplete application behavior.

## Scope Boundary

| Topic | Skill |
|---|---|
| Slice workflow (`plan` -> `generate` -> `verify`) | `crablet-app-dev` |
| Local invocation, output path, MCP vs terminal | `crablet-local-dev` |
| Provider setup, artifact ownership, repair/recovery | `crablet-codegen` |
| Generator internals, `CodegenLlmClient`, adapter code | `crablet-maintainer` |

## Provider Setup

Five named providers are supported by `crablet-codegen`.

| Provider | `CODEGEN_LLM_PROVIDER` | Key env vars | Notes |
|---|---|---|---|
| Anthropic | `anthropic` (default; may omit var) | `ANTHROPIC_API_KEY` | Default model: `claude-sonnet-4-6` |
| OpenAI | `openai` | `OPENAI_API_KEY`, `OPENAI_MODEL` or `CODEGEN_LLM_MODEL` | Both model vars work |
| DeepSeek | `deepseek` | `DEEPSEEK_API_KEY`; optional `DEEPSEEK_BASE_URL`, `DEEPSEEK_MODEL` | Defaults: `https://api.deepseek.com`, `deepseek-chat` |
| Ollama | `ollama` | `OLLAMA_MODEL`; optional `OLLAMA_BASE_URL` | Default base URL: `http://localhost:11434/v1` |
| OpenAI-compatible | `openai-compatible` | `CODEGEN_LLM_API_KEY`, `CODEGEN_LLM_BASE_URL`, `CODEGEN_LLM_MODEL`; or `OPENAI_COMPATIBLE_API_KEY`, `OPENAI_COMPATIBLE_BASE_URL`, `OPENAI_COMPATIBLE_MODEL` | Use for OpenAI API-compatible endpoints not listed above |

`CODEGEN_LLM_API_KEY`, `CODEGEN_LLM_BASE_URL`, and `CODEGEN_LLM_MODEL` are provider-neutral
overrides. They are read first for any selected provider. Provider-specific variables such as
`ANTHROPIC_API_KEY` and `OPENAI_MODEL` are the normal documented path; `CODEGEN_LLM_*` variables
are override shortcuts.

Ollama can also be addressed through `openai-compatible` using `CODEGEN_LLM_*` variables. The
named `ollama` provider is simpler for local setups.

`CODEGEN_LLM_MAX_TOKENS` defaults to `8096`.

When running the JAR directly from the root repo, remember that `codegen.claude-md-path` must
resolve to `crablet-codegen/CLAUDE.md`. The root Makefile targets handle this by running from the
`crablet-codegen` module.

## Artifact Ownership

Generator-owned files can be overwritten by the next generation run. User-owned `@Component`
implementation classes are not touched by the generator.

| Artifact | Generated as | Regeneration behavior |
|---|---|---|
| Domain events (sealed interface + records) | Concrete records | Overwritten; do not hand-edit |
| Command records | Concrete records | Overwritten; do not hand-edit |
| `StateProjector`, `QueryPatterns`, state records | Concrete `@Component` / classes | Overwritten; do not hand-edit |
| View projectors | Concrete `@Component` | Overwritten; do not hand-edit; verify computed fields and validation ranges manually |
| Flyway migrations | `.sql` files | Overwritten; do not hand-edit |
| Command handler interfaces | Empty Java interface | Overwritten; do not hand-edit |
| Automation handler interfaces | Metadata-only Java interface | Overwritten; do not hand-edit |
| Outbox publisher interfaces | Metadata-only Java interface | Overwritten; do not hand-edit |
| Scenario test scaffolds | Plain JUnit 5 class | Written once on first `generate`; never overwritten |
| `@Component` command handler implementation | User class | Never touched by generator |
| `@Component` automation implementation | User class | Never touched by generator |
| `@Component` outbox publisher implementation | User class | Never touched by generator |

Key rule: if behavior is wrong, create or edit the `@Component` implementation class. Do not put
business logic in the generated interface.

## Repair Cycle

The generator runs compile -> fix -> compile automatically, up to 3 times. Output shows
`[RepairAgent]` lines during repair. If all 3 attempts fail, the build prints a `[WARN]` with the
compiler errors above it.

When repair is exhausted:

1. Read the compiler error. It names the file and line.
2. Fix the generator-owned file manually, or delete that generated file and rerun generation, as an
   emergency unblock. Manual fixes to generator-owned files are temporary; the next generation run
   can overwrite them.
3. Make the durable fix in `event-model.yaml` when the model is under-specified. If the model is
   correct and the error repeats, fix the generator or its prompts/templates.

Do not delete user `@Component` implementation classes.

## Recovery Decision Tree

These are the five failure modes documented in `crablet-codegen/README.md` under **When Generation
Fails**. `$ref` schema errors are a subcase of YAML/model parse failure.

| Symptom | Cause | Fix |
|---|---|---|
| Compilation fails after 3 repair attempts | Generator cannot resolve the error | Read the compiler error. Fix the generated file as a temporary unblock, or fix `event-model.yaml` / generator templates and rerun |
| No `===FILE: ...===` blocks in output | Prompt exceeded context, or `claude-md-path` is misconfigured | Check `claude-md-path`; rerun generation. Transient API errors are retried on the next invocation |
| YAML parse error / `$ref` not found | Malformed `event-model.yaml` or missing schema entry | Run `plan` first. It parses without a model call and shows the error immediately |
| `ANTHROPIC_API_KEY is not set` or similar | Provider not configured | Set the provider-specific env var or switch `CODEGEN_LLM_PROVIDER` |
| Generated code compiles but behavior is wrong | Business logic missing; generated interfaces have no logic | Create a `@Component` class implementing the generated interface. Never edit the interface |

## Scenario Test Scaffolding

Add a `scenarios` section to `event-model.yaml` during the Event Modeling workshop to describe
expected behavior. On the first `generate` run the generator writes a JUnit 5 test skeleton for
each scenario into `src/test/java/{basePackage}/test/`. The file is never overwritten again —
it is user-owned from that point.

```yaml
scenarios:
  - name: Submit loan application
    steps:
      - keyword: Given
        text: a loan application does not exist
      - keyword: When
        text: submit loan application for applicant APPLICANT_001
      - keyword: Then
        text: the system records LoanApplicationSubmitted
```

Generated output (`src/test/java/com/example/loan/test/SubmitLoanApplicationScenarioTest.java`):

```java
class SubmitLoanApplicationScenarioTest {

    @Test
    @DisplayName("Submit loan application")
    void submitLoanApplication() {
        // Given: a loan application does not exist

        // When: submit loan application for applicant APPLICANT_001

        // Then: the system records LoanApplicationSubmitted
    }
}
```

Scenarios are structural model elements authored during the workshop.

## Source References

- `crablet-codegen/README.md` — provider configuration and recovery guidance.
- `crablet-codegen/CLAUDE.md` — exact generated artifact shape templates.
