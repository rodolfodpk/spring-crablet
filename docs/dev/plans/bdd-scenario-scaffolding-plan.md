# Plan: BDD Scenario Test Scaffolding (without Gherkin)

## Context

The `feat/gherkin-import` PR is being closed without merging. Its core mistake was framing Gherkin `.feature` files as a workflow entry point — canonical Event Modeling doesn't produce `.feature` files.

The right design: **scenarios authored in `event-model.yaml` during the workshop drive generated JUnit 5 test scaffolding**. No `.feature` files anywhere. The infrastructure for this (model records, planner) was built in the closed PR but never wired to actual generation.

This plan ports only the clean, non-Gherkin pieces and implements the missing `ScenariosAgent`.

---

## Branch

New branch from `main` (`feat/bdd-scenarios`). None of the model changes landed in main — the PR was closed without merging. Do NOT cherry-pick the full PR commit (it includes `GherkinImportService`, `import-gherkin` CLI, MCP wiring). Re-implement only what's listed below.

---

## Step 1 — Port model records (new files, no Gherkin dependency)

**New files** (copy cleanly from `feat/gherkin-import`, zero changes needed):
- `embabel-codegen/src/main/java/com/crablet/codegen/model/ScenarioSpec.java`
- `embabel-codegen/src/main/java/com/crablet/codegen/model/ScenarioStepSpec.java`

**Modified files** (port only the scenario-related additions):
- `EventModel.java` — add `scenarios` field and `scenarios()` accessor (`List<ScenarioSpec>`, defaults to `List.of()`)
- `ArtifactPlanner.java` — add the `testClass` loop (lines 74-81 on the branch)
- `PlannedArtifact.java` — add `testClass()` factory method
- `SchemaResolver.java` — pass `scenarios` through (one-liner)

Do NOT port: `GherkinImportService`, `CodegenCommand` import-gherkin case, `McpServer` import-gherkin tool, any `.feature` example files, `bdd-input-import-plan.md`.

---

## Step 2 — Add Scenario Test Template to `embabel-codegen/CLAUDE.md`

Add a `### Scenario Test Template` section (heading must contain `"Scenario Test"` for `TemplateLoader.load("Scenario Test")` to find it).

Content: documents the generated class shape. Contract:
- Package: `{basePackage}.test`
- Class name: `{sanitizedBase}ScenarioTest` where `sanitizedBase` is the PascalCase result of `toJavaIdentifier(scenarioName)`
- Method name: lowerCamelCase of that same sanitized base (lowercase the first character)
- Imports: `org.junit.jupiter.api.DisplayName` and `org.junit.jupiter.api.Test` only — no Spring, no Crablet
- Each `Given`/`When`/`Then` step → labeled comment block; `And`/`But` → `// And:` continuation

Reference example:
```java
package com.example.loan.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

---

## Step 3 — Implement `ScenariosAgent`

**New file:** `embabel-codegen/src/main/java/com/crablet/codegen/agents/ScenariosAgent.java`

Key decisions:

**`@Component`** — required for Spring constructor injection in `CodegenPipeline`.

**No LLM call** — output is 100% deterministic from the model; Java string rendering, not `llm.complete()`.

**No `FileWriterTool`** — that tool always overwrites. Agent writes via `Files.writeString(path, content, StandardOpenOption.CREATE_NEW)` and catches `FileAlreadyExistsException` to skip. This is atomic — no TOCTOU race between `exists()` and `writeString`.

**Test output path**: walk the absolute `outputDir` path segment by segment looking for the `src` / `main` / `java` triple. Replace `main` with `test`. If that triple is not found, fail with a clear `IllegalArgumentException("Cannot derive test output path from: " + outputDir + " — expected a path containing src/main/java")`. Do not walk up parent directories; that risks writing outside the app root.

**Name sanitization** — use the same `toJavaIdentifier` algorithm as `ArtifactPlanner`. Additionally:
- Safe prefix: if the result is blank or starts with a digit, prepend `Generated`
- `@DisplayName` string: escape `\` as `\\` and `"` as `\"` in the scenario name
- Step text: collapse `\r` and `\n` to a space before embedding in comments

**Step rendering**:
```
Given/When/Then  →  // {keyword}: {text}\n
And              →  // And: {text}\n   (continuation)
But              →  // But: {text}\n   (continuation — preserve workshop wording)
```

Constructor takes `TemplateLoader` (for documentation purposes), no `CodegenLlmClient`.

---

## Step 4 — Wire into `CodegenPipeline`

**File:** `embabel-codegen/src/main/java/com/crablet/codegen/pipeline/CodegenPipeline.java`

- Inject `ScenariosAgent` in constructor
- Call `scenariosAgent.generate(resolved, outputDir)` after `outboxAgent.generate()`, before the repair loop
- Test files are in `src/test/java`, outside `mvn compile` scope — repair loop unchanged

---

## Step 5 — Update `docs/user/examples/event-model-schema.json`

Add the `scenarios` array to the JSON schema. Only `name` is required; `tags` and `steps` are optional and default to empty, matching the Java records:
- `name` (string, required)
- `tags` (array of string, optional)
- `steps` (array of objects with `keyword` string and `text` string, optional)

Without this update, `scenarios:` blocks in YAML are documented but not schema-valid, which breaks tooling that validates against the schema.

---

## Step 6 — Add `ScenariosAgentTest`

**New file:** `embabel-codegen/src/test/java/com/crablet/codegen/agents/ScenariosAgentTest.java`

JUnit 5, `@TempDir`, no Spring context. Test cases:

1. `skipsWhenScenariosEmpty` — no files written, no exception
2. `writesTestFileForScenario` — correct file path, contains `@Test`, `@DisplayName`, exact method name `void submitLoanApplication()` (lowerCamelCase of PascalCase base), `// Given:`, `// When:`, `// Then:`
3. `skipsExistingFile` — pre-create target with sentinel content; call `generate()`; assert content unchanged (atomic CREATE_NEW semantics honored)
4. `andAndButRenderWithOwnKeyword` — `And` step appears as `// And:`, `But` step appears as `// But:`, neither appears as `// Given:`/`// When:`/`// Then:`
5. `derivesTestPathFromMainJava` — `outputDir` path contains `src/main/java`; file lands under `src/test/java`
6. `failsWithClearMessageWhenNoMainJavaSegment` — `outputDir` has no `src/main/java` in path; assert `IllegalArgumentException` with message containing `src/main/java`
7. `escapesDisplayNameQuotesAndBackslashes` — scenario name containing `"` and `\` is escaped correctly in `@DisplayName`
8. `collapsesNewlinesInStepText` — step text containing `\n` is rendered as a single-line comment

---

## Step 7 — Update docs and SKILL.md

Write fresh on the new branch (do not carry over the Gherkin-forward framing from `feat/gherkin-import`):

**`docs/user/ai-tooling/EVENT_MODEL_FORMAT.md`** — add a short `scenarios` section: "Optional. Scenarios authored during the Event Modeling workshop. Each scenario produces a JUnit 5 test scaffold on first `generate` run."

**`docs/user/ai-tooling/FEATURE_SLICE_WORKFLOW.md`** — add a note in the modeling step: scenarios are an optional model element; if present they produce generated test skeletons.

**`docs/user/ai-tooling/AI_FIRST_WORKFLOW.md`** — same brief note, no Gherkin mention.

**`docs/user/ai-tooling/EVENT_MODELING.md`** — one sentence: scenarios in the model are the BDD on-ramp; no `.feature` files needed.

**`.claude/skills/crablet-codegen/SKILL.md`** — replace the `## Gherkin Import` section with `## Scenario Test Scaffolding`. Update the artifact ownership table to add: `Scenario test scaffold | JUnit 5 class | Written once; never overwritten`.

---

## Branch starting point

Start from `main`. If working from the current `feat/gherkin-import` branch instead, explicitly remove all Gherkin additions before committing: `GherkinImportService.java`, `import-gherkin` CLI/MCP wiring in `CodegenCommand` and `McpServer`, `GherkinImportServiceTest`, the `.feature` example file, `bdd-input-import-plan.md`, the Gherkin additions to `embabel-codegen/README.md`, `docs/user/ai-tooling/AI_SKILLS.md`, `templates/crablet-app/*` Gherkin notes, and the uncommitted "Gherkin feature import" routing edit in the repo-root `CLAUDE.md`. Starting from `main` is strongly preferred.

---

## Critical files

| File | Action |
|------|--------|
| `embabel-codegen/src/main/java/com/crablet/codegen/model/ScenarioSpec.java` | New (port) |
| `embabel-codegen/src/main/java/com/crablet/codegen/model/ScenarioStepSpec.java` | New (port) |
| `embabel-codegen/src/main/java/com/crablet/codegen/model/EventModel.java` | Add `scenarios` field |
| `embabel-codegen/src/main/java/com/crablet/codegen/planning/ArtifactPlanner.java` | Add `testClass` loop |
| `embabel-codegen/src/main/java/com/crablet/codegen/planning/PlannedArtifact.java` | Add `testClass()` factory |
| `embabel-codegen/src/main/java/com/crablet/codegen/pipeline/SchemaResolver.java` | Pass `scenarios` through |
| `embabel-codegen/src/main/java/com/crablet/codegen/agents/ScenariosAgent.java` | **New — `@Component`, no LLM** |
| `embabel-codegen/src/main/java/com/crablet/codegen/pipeline/CodegenPipeline.java` | Inject + call `ScenariosAgent` |
| `embabel-codegen/CLAUDE.md` | Add `### Scenario Test Template` |
| `docs/user/examples/event-model-schema.json` | Add `scenarios` array to schema |
| `embabel-codegen/src/test/java/com/crablet/codegen/agents/ScenariosAgentTest.java` | New — 8 test cases |
| `.claude/skills/crablet-codegen/SKILL.md` | Replace Gherkin Import section |
| 4 docs files (EVENT_MODEL_FORMAT, FEATURE_SLICE, AI_FIRST_WORKFLOW, EVENT_MODELING) | Minimal fresh framing |

---

## Verification

```bash
cd embabel-codegen && mvn test
```

Watch: `ScenariosAgentTest` (all 8), `ArtifactPlannerTest` (unchanged), `EventModelParsingTest` (scenarios default to empty), `McpServerTest` (tool count stays the same — no new MCP tool added).

Manual:
1. Run `generate` against a model with `scenarios` → confirm `src/test/java/.../FooScenarioTest.java` is created
2. Run `generate` again → confirm `[ScenariosAgent] Skipping ... — already exists` is logged
3. Run `generate` against a model with no `scenarios` → confirm `[ScenariosAgent]` never prints
4. `mvn test-compile` in the generated app → confirm scaffold compiles with zero changes
