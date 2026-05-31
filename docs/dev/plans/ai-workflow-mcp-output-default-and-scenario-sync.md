# Plan: AI Workflow Improvements — MCP Output Default + Scenario Sync

## Context

Two known gaps in the crablet-codegen AI workflow:

1. `crablet_generate` MCP tool defaults `output` to `.` (the working directory), but the starter
   template always needs `src/main/java`. This is documented as a known gotcha in the
   `crablet-app-dev` skill, which means it is actively biting first-time MCP users and must be
   worked around manually every time.

2. BDD scenario test scaffolds are write-once (user-owned after first generation), which is the
   right policy — but there is no way to detect when the model and the on-disk files diverge. If
   you rename or add a scenario in `event-model.yaml`, the stale test file persists silently with
   no warning.

---

## Fix 1 — MCP `crablet_generate` output default

**Change:** Update the `crablet_generate` MCP default for `output` from `"."` to `"src/main/java"`.
The CLI default stays `"."` because the Makefile passes `--output src/main/java` explicitly and
CLI users are expected to be explicit.

### Files

**`crablet-codegen/src/main/java/com/crablet/codegen/cli/McpServer.java`**

- Line 138: update tool schema description:
  `"Output directory for generated source files (default: .)"` → `"Output directory for generated source files (default: src/main/java)"`
- Line 206: `args.path("output").asText(".")` → `args.path("output").asText("src/main/java")`

**`.claude/skills/crablet-app-dev/SKILL.md`** and **`templates/crablet-app/.claude/skills/crablet-app-dev/SKILL.md`**

- Remove the bullet in `## App Gotchas`:
  `- The MCP tool defaults \`output\` to \`.\`, which is wrong for the starter template. Use \`src/main/java\`.`

**Docs sweep (part of this fix, not optional):**
Run `rg "default: \\\." crablet-codegen/ docs/ .claude/ templates/` and
`rg "crablet_generate" docs/ .claude/ templates/` to catch any remaining help text, doc examples,
or skill prose that still describes `output` defaulting to `.`. Update every hit.
No change to `CodegenCommand.printHelp()` — it already shows `--output src/main/java` as the
example value, not as the default.

No new tests needed — the default is a string constant. `ScenarioScaffoldGenerator.deriveTestOutputDir`
already throws `IllegalArgumentException` with a clear message if the path does not contain
`src/main/java`, which provides an implicit validation safety net.

---

## Fix 2 — Scenario sync report

**Change:** Add a `syncReport` method to `ScenarioScaffoldGenerator` and expose it as a new
`sync-scenarios` CLI command, `crablet_sync_scenarios` MCP tool, and `make sync-scenarios` Makefile
target. Read-only — never writes files.

### Data model

**New record `MissingScenario`** (top-level, package `com.crablet.codegen.scaffold`):

```java
public record MissingScenario(String scenarioName, String expectedFileName) {}
```

Carries both the human-readable scenario name and the derived file name, so `render()` can print
`- Reject application  →  RejectApplicationScenarioTest.java` without re-deriving anything.

**New record `ScenarioSyncReport`** (top-level, same package):

```java
public record ScenarioSyncReport(
    List<MissingScenario> inModelNotOnDisk,  // in event-model.yaml but no file on disk
    List<String> onDiskNotInModel            // *ScenarioTest.java stem names with no model scenario
) {
    public boolean isClean() { return inModelNotOnDisk.isEmpty() && onDiskNotInModel.isEmpty(); }
    public String render() { ... }
}
```

`inModelNotOnDisk` uses `MissingScenario` because the report needs both fields.
`onDiskNotInModel` is `List<String>` of stem names (e.g. `ApproveApplicationScenarioTest`) because
there is no scenario name to pair with a stale file.

Rendered output (clean):
```
All scenario test scaffolds are in sync.
  2 scenario(s) in model, 2 matching file(s) on disk.
```

Rendered output (drift):
```
Scenario sync report — drift detected

In model, not on disk (1):
  - Reject application  →  RejectApplicationScenarioTest.java

On disk, not in model (1):
  - ApproveApplicationScenarioTest.java

Run 'make generate' to write missing scaffolds. Delete stale test files manually.
```

### Scan scope (explicit decision)

**Scope:** `{testOutputDir}/{basePackage-as-path}/test/` — i.e., the package `{basePackage}.test`
directory only.

**Accepted limitation:** If `basePackage` changes between generate runs, test files written under
the old package will not appear in `onDiskNotInModel` because they live outside the scan root.
This is acceptable: the scan is scoped to the current model's declared package, which is the only
package `generate` writes into. Document this in the `render()` output header or in CLI help.

No recursive `src/test/java` scan — that would catch files from other generators or hand-written
tests named `*ScenarioTest.java` outside the Crablet package, causing false positives.

### New method: `ScenarioScaffoldGenerator.syncReport`

**`crablet-codegen/src/main/java/com/crablet/codegen/scaffold/ScenarioScaffoldGenerator.java`**

```java
public ScenarioSyncReport syncReport(EventModel model, Path outputDir) {
    // 1. for each model scenario: derive expectedStem = toJavaIdentifier(name) + "ScenarioTest"
    //    build Map<String, MissingScenario> expectedByFileName
    // 2. derive testPkgDir = packageToPath(deriveTestOutputDir(outputDir), basePackage + ".test", "")
    //    (directory, not a file)
    // 3. if testPkgDir does not exist:
    //    → all expected are inModelNotOnDisk, onDiskNotInModel is empty
    // 4. scan testPkgDir for *ScenarioTest.java (non-recursive, direct children only)
    //    build Set<String> actualStems (strip .java suffix)
    // 5. inModelNotOnDisk = expectedByFileName entries where stem not in actualStems
    //    onDiskNotInModel = actualStems not in expectedByFileName keys
    // 6. return new ScenarioSyncReport(inModelNotOnDisk, onDiskNotInModel)
}
```

Reuses existing private `toJavaIdentifier`, `deriveTestOutputDir`, and `packageToPath` helpers.

### CLI: `sync-scenarios` command

**`crablet-codegen/src/main/java/com/crablet/codegen/cli/CodegenCommand.java`**

- Inject `ScenarioScaffoldGenerator` in the constructor
- Add `case "sync-scenarios" -> runSyncScenarios(parseFlags(args, 1));` to the switch
- `runSyncScenarios`: reads `--model` (default `event-model.yaml`) and `--output`
  (default `src/main/java`), calls `syncReport`, prints rendered result, exits 1 if `!isClean()`
- Document in `printHelp()`

Exit code 1 makes this CI-friendly (e.g. a `make check` variant can include sync).

### MCP: `crablet_sync_scenarios` tool

**`crablet-codegen/src/main/java/com/crablet/codegen/cli/McpServer.java`**

- Inject `ScenarioScaffoldGenerator` in the constructor
- Add `crablet_sync_scenarios` to `toolsListResult()`:
  - `model` param (default: `event-model.yaml`)
  - `output` param (default: `src/main/java`)
- Add `case "crablet_sync_scenarios" -> callSyncScenarios(args);` to dispatch switch
- `callSyncScenarios`: returns `report.render()` as the tool text **and sets `isError=true` when
  `!report.isClean()`**. Drift is a logically significant failure for automated callers; this makes
  the MCP tool's failure signal consistent with the CLI's exit-1. Exceptions (model parse failure,
  I/O error) also set `isError=true`, as they do in the existing tools. When the report is clean,
  `isError` is omitted (false by default).

- **Implementation note — avoid forcing drift through an exception.** The current `toolCallResult`
  sets `isError` only inside the `catch` block, so it is exception-driven today. Rather than
  throwing a synthetic exception for drift, introduce a small private record:
  ```java
  private record ToolResult(String text, boolean isError) {}
  ```
  Migrate `callGenerate`, `callPlan`, etc. to return `ToolResult` (or keep them returning `String`
  and wrap at the call site), and have `callSyncScenarios` return
  `new ToolResult(report.render(), !report.isClean())`. `toolCallResult` then reads `isError` from
  the returned record rather than solely from the catch block. The exception path stays unchanged.
- Update class-level Javadoc to list `crablet_sync_scenarios` alongside the existing tools

### Makefile

**`templates/crablet-app/Makefile`**

- Add `sync-scenarios` to `.PHONY`
- Add target:
  ```makefile
  sync-scenarios:
  	@java -jar $(CRABLET_CODEGEN_JAR) sync-scenarios --model event-model.yaml --output src/main/java
  ```
- Add to `help` target output: `"  sync-scenarios - Report drift between event-model.yaml scenarios and generated test scaffolds"`

### Tests

**`crablet-codegen/src/test/java/com/crablet/codegen/scaffold/ScenarioScaffoldGeneratorTest.java`**

Four new tests for `syncReport` using the existing `@TempDir` + `mainJavaDir()` pattern:

- `syncReportIsCleanWhenAllPresent()` — model scenario + matching file both exist → `isClean() == true`
- `syncReportDetectsInModelNotOnDisk()` — model has scenario, no file → entry in `inModelNotOnDisk` with correct `scenarioName` and `expectedFileName`
- `syncReportDetectsOnDiskNotInModel()` — file exists, no model scenario → stem in `onDiskNotInModel`
- `syncReportCleanWhenNeitherModelNorDisk()` — empty model, no test dir → `isClean() == true`

Two new tests for `ScenarioSyncReport.render()` directly (pure record, no I/O):

- `renderCleanReport()` — assert rendered string contains "in sync" and the correct counts
- `renderDriftReport()` — assert rendered string contains both drift sections, the `→` arrow
  notation for missing scaffolds, and the "Delete stale" instruction

**`crablet-codegen/src/test/java/com/crablet/codegen/cli/McpServerTest.java`** (if this test exists):

- Constructor call must pass a `ScenarioScaffoldGenerator` instance (or mock)
- Tool list count assertion: 4 → 5
- Tool name list must include `crablet_sync_scenarios`

---

## Verification

1. **Unit tests:** `./mvnw test -pl crablet-codegen` — all existing + new tests pass
2. **MCP default:** Run `crablet_generate` via Claude Code MCP without specifying `output`; confirm
   files land in `src/main/java/`, not at the project root
3. **sync-scenarios CLI — drift case:** From a template app with a model scenario that has no test
   file: `java -jar tools/crablet-codegen.jar sync-scenarios` prints drift report and exits 1
4. **sync-scenarios CLI — clean case:** After `make generate`, re-run `sync-scenarios`; exits 0
5. **sync-scenarios MCP — drift:** Call `crablet_sync_scenarios` with a stale model; response
   contains `isError=true` and the drift report text
5a. **sync-scenarios MCP — clean:** Call after `make generate`; response has no `isError` flag
6. **make sync-scenarios:** From the template app root, `make sync-scenarios` runs and outputs the report
7. **Docs sweep result:** No remaining occurrences of `"default: ."` or `"output=."` for
   `crablet_generate` in docs, skills, or templates
8. **Skill gotcha removal:** `crablet-app-dev` App Gotchas no longer mentions the output default
