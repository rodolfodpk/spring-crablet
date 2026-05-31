# AI Workflow Feasibility Assessment

_Date: 2026-05-24_

## Verdict: Feasible, with known gaps

---

## What's fully built

**The structural layer is complete.** The pipeline is real, not aspirational:

- `CodegenPipeline` runs all five agents (events → commands → views → automations → outbox) plus
  scenario scaffold and a 3-attempt compile-and-repair loop
  (`crablet-codegen/.../pipeline/CodegenPipeline.java`).
- `ArtifactPlanner` is deterministic and well-tested — plan output is verified against the loan
  fixture without LLM involvement (`planning/ArtifactPlannerTest.java`).
- `McpServer` exposes `crablet_plan`, `crablet_generate`, `crablet_init`,
  `crablet_sync_scenarios` as real MCP tools — Claude Code and Cursor can invoke them.
- Agent prompts (`CommandsAgent`, `ViewsAgent`, etc.) embed the framework patterns, DCB strategy
  mapping, and YAVI constraints, not generic Java prompts.
- `crablet-codegen/CLAUDE.md` is a concrete template used by `TemplateLoader` at runtime — the
  agents know the exact interface shapes to produce.

**The two-step loop (workshop → YAML → code) is the right architecture.** The model is the durable
artifact, not a transcript. The `plan` step lets you review artifact names before touching files.
The repair loop catches structural compilation errors.

---

## Where the real risk lives

**1. Code generation quality is untested end-to-end.**
`CommandsAgentTest` only checks that the prompt contains the right facts — it captures but never
evaluates the LLM response. No test runs `generate` against a real app and verifies the output
compiles. The `make codegen-check` target only exercises `plan`, not generation.

**2. The repair loop has a hard ceiling.**
Three repair attempts is fine for simple structural errors but may not be enough for a model with
multi-aggregate DCB or complex automation wiring. There is no escalation path after attempt 3 — it
prints a warning and exits.

**3. Model completeness is the human gate.**
Generated code quality is directly proportional to `event-model.yaml` richness. Forgetting
`guardEvents`, a wrong pattern, or a missing `produces` entry propagates into broken code. The
workflow's "ask for missing facts" dialogue is critical — that is why the Claude Code skills exist.

**4. Test scaffolds are stubs.**
Generated tests (`SubmitLoanApplicationScenarioTest`) have `// Given/When/Then` comments but no
assertions. CI will pass them, which masks missing business logic coverage.

---

## Bottom line

The infrastructure is substantially real. Model parsing, planning, prompt construction, MCP tools,
and the pipeline are all production-grade code, not scaffolding. What is not yet in place is
**hardening evidence**: no CI-level test runs `generate` end-to-end and checks a known-good app
compiles. That is the main gap between "feasible" and "reliable."

The workflow will work well for clean, well-modeled greenfield slices. It will struggle on complex
DCB boundaries, brownfield app integration, or underdefined models. Those are fixable by either
adding end-to-end generation tests or deliberately running the loan-slice through `make generate`
and committing the result as a living fixture.
