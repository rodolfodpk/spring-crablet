# AI Workflow Feasibility Assessment

_Date: 2026-07-02 (updated from 2026-05-24)_

## Verdict: Feasible — default generate is deterministic

---

## What's fully built

**The generation pipeline is deterministic.** `generate`, `plan`, `init`, `k8s`, and
`sync-scenarios` require no LLM API key:

- `CodegenPipeline` runs five deterministic generators (events → commands → views →
  automations → outbox) plus `ScenarioScaffoldGenerator`.
- `ArtifactPlanner` is deterministic and well-tested — plan output is verified against the
  loan fixture without any LLM involvement (`planning/ArtifactPlannerTest.java`).
- `McpServer` exposes `crablet_plan`, `crablet_generate`, `crablet_init`,
  `crablet_sync_scenarios` as real MCP tools — Claude Code and Cursor can invoke them.
- `crablet-codegen/CLAUDE.md` is a human-readable shape contract for generated artifact
  shapes; it is not injected into LLM prompts at runtime.

**The two-step loop (workshop → YAML → code) is the right architecture.** The model is the
durable artifact, not a conversation transcript. The `plan` step lets you review artifact
names before touching files. `generate` produces the same output every time from the same
YAML.

---

## Where the real risk lives

**1. Generator unit tests are incomplete.**
Only `CommandsGeneratorTest` exists. Events, views, automations, and outbox generators are
untested at the unit level. A generator regression will not be caught until the
`codegen-snapshot-verify` CI step.

**2. No regenerate-and-diff gate yet.**
`examples/loan-generated-snapshot` compiles in CI (`make codegen-snapshot-verify`) but is
not compared against a fresh `generate` run. The snapshot can drift from the generator
silently — the only signal is a compile error.

**3. Model completeness is the human gate.**
Generated code quality is directly proportional to `event-model.yaml` richness. Forgetting
`guardEvents`, a wrong pattern, or a missing `produces` entry produces structurally broken
or incorrect code. The event-modeling skills exist precisely to guide complete models.

**4. Test scaffolds are stubs.**
Generated tests (`SubmitLoanApplicationScenarioTest`) have `// Given/When/Then` comments
but no assertions. CI will pass them, which masks missing business logic coverage. This is
intentional — scenario stubs are human-owned from first write.

---

## Bottom line

The infrastructure is substantially complete. Model parsing, planning, deterministic code
generation, MCP tools, and the pipeline are production-grade. The remaining gaps are
hardening gaps: generator unit test coverage and a regenerate-and-diff CI gate. Both are
tracked in `docs/dev/plans/ai-workflow-trust-hardening.md`.

No API key is needed for the default workflow. An LLM is used only during the Event Modeling
workshop phase (producing `event-model.yaml`), not during `generate`.
