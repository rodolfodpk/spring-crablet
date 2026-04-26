# Docs: Framework vs AI-Tooling Split

**Status: EXECUTED** (2026-04-25) — `docs/ai-tooling/` created; four files moved; links and
`scripts/verify-docs.sh` updated; `README` documentation section split into **Framework** vs **AI
tooling**; **Framework Path** and **AI-First Path** body sections reordered (framework first). `make
docs-check` passes.

## Goal

Give the `docs/` folder and root README explicit, separate sections for:
- **Framework** — the Java runtime (EventStore, commands, views, outbox, automations, configuration, operations)
- **AI Tooling** — event-model-driven codegen (embabel-codegen, MCP server, templates, workflows)

## Motivation

Since `embabel-codegen` and the AI-first workflow were introduced, the docs/ flat list and README documentation section blend two audiences. A framework user doesn't need to wade through codegen workflow docs; an AI-tooling user shouldn't have to find codegen docs buried in an undifferentiated list.

---

## Step 1 — Create `docs/ai-tooling/` and move 4 files

`DOCS_VERIFICATION.md` stays at `docs/DOCS_VERIFICATION.md` — it covers repo-wide checks (deployment wording, tutorial fixtures, outbox API shape, markdown links) and is not AI-tooling-specific.

| Current | New |
|---|---|
| `docs/AI_FIRST_WORKFLOW.md` | `docs/ai-tooling/AI_FIRST_WORKFLOW.md` |
| `docs/FEATURE_SLICE_WORKFLOW.md` | `docs/ai-tooling/FEATURE_SLICE_WORKFLOW.md` |
| `docs/EVENT_MODELING.md` | `docs/ai-tooling/EVENT_MODELING.md` |
| `docs/EVENT_MODEL_FORMAT.md` | `docs/ai-tooling/EVENT_MODEL_FORMAT.md` |

Use `git mv` so history is preserved.

---

## Step 2 — Fix all links (exhaustive pass)

### 2a — Inbound links to moved files

Two complementary rg checks find all references — run both before editing.

**Check 1** catches old root-prefixed paths (`docs/FILENAME.md`). Exclude `docs/plans/**` entirely — plan files are historical records and may keep stale references without breaking the live docs.

```bash
rg 'docs/AI_FIRST_WORKFLOW\.md|docs/FEATURE_SLICE_WORKFLOW\.md|docs/EVENT_MODELING\.md|docs/EVENT_MODEL_FORMAT\.md' \
  --type md \
  --glob '!.git/**' \
  --glob '!docs/plans/**'
```

**Check 2** catches bare-filename relative links (`](AI_FIRST_WORKFLOW.md)` or `](./AI_FIRST_WORKFLOW.md)`). The pattern matches only targets that start with the filename (no preceding path separator), so links updated to `docs/ai-tooling/...` or `../ai-tooling/...` will not produce false hits.

```bash
rg '\]\((?:\.\/)?(?:AI_FIRST_WORKFLOW|FEATURE_SLICE_WORKFLOW|EVENT_MODELING|EVENT_MODEL_FORMAT)\.md\b' \
  --type md \
  --glob '!docs/ai-tooling/**' \
  --glob '!.git/**' \
  --glob '!docs/plans/**'
```

The union of both outputs is the authoritative list to fix. Known files (convenience checklist):

| File | Paths to update |
|---|---|
| `README.md` | AI_FIRST_WORKFLOW, FEATURE_SLICE_WORKFLOW, EVENT_MODELING, EVENT_MODEL_FORMAT |
| `CLAUDE.md` | AI_FIRST_WORKFLOW, FEATURE_SLICE_WORKFLOW, EVENT_MODEL_FORMAT |
| `docs/QUICKSTART.md` | AI_FIRST_WORKFLOW, EVENT_MODELING, EVENT_MODEL_FORMAT, FEATURE_SLICE_WORKFLOW |
| `docs/CREATE_A_CRABLET_APP.md` | AI_FIRST_WORKFLOW, EVENT_MODEL_FORMAT |
| `embabel-codegen/README.md` | AI_FIRST_WORKFLOW, FEATURE_SLICE_WORKFLOW, EVENT_MODEL_FORMAT |
| `templates/README.md` | AI_FIRST_WORKFLOW |
| `templates/crablet-app/README.md` | scan; rg will confirm |
| `wallet-example-app/README.md` | scan; rg will confirm |

### 2b — Outbound links inside moved files

Moving `docs/*.md` to `docs/ai-tooling/*.md` changes the depth of every relative link. Fix **every** relative link inside each moved file — not just sibling links but also any paths pointing at:
- `../assets/`, `../examples/`, `../tutorials/`
- `../../templates/`, `../../wallet-example-app/`
- sibling docs that stay in `docs/` (e.g. `../QUICKSTART.md`, `../TUTORIAL.md`, `../CREATE_A_CRABLET_APP.md`)
- module READMEs (e.g. `../../crablet-eventstore/README.md`)

Cross-links between the 4 moved files become sibling-relative (e.g. `./EVENT_MODEL_FORMAT.md`).

### 2c — Update `scripts/verify-docs.sh`

In the `check_links` function, search for the **three** existing old `docs/...` path strings inside the `markdown_files` array and replace them, then **add** `EVENT_MODELING.md` as a new entry (it was not in the array before the move):

```
"docs/AI_FIRST_WORKFLOW.md"      →  "docs/ai-tooling/AI_FIRST_WORKFLOW.md"
"docs/FEATURE_SLICE_WORKFLOW.md" →  "docs/ai-tooling/FEATURE_SLICE_WORKFLOW.md"
"docs/EVENT_MODEL_FORMAT.md"     →  "docs/ai-tooling/EVENT_MODEL_FORMAT.md"
(add new)                            "docs/ai-tooling/EVENT_MODELING.md"
```

(`docs/DOCS_VERIFICATION.md` stays — no change for that entry.)

---

## Step 3 — Restructure the README documentation section

Replace the current flat three-group link list with two labelled groups:

### Framework
QUICKSTART · TUTORIAL · CREATE_A_CRABLET_APP · LEARNING_MODE · COMMANDS_FIRST_ADOPTION ·
MODULES · PUBLIC_API · DEPLOYMENT_TOPOLOGY · DCB_AND_CRABLET · COMMAND_PATTERNS ·
CONFIGURATION · BUILD · PERFORMANCE · TROUBLESHOOTING · UPGRADE ·
MANAGEMENT_API · FAULT_RECOVERY · LEADER_ELECTION · OBSERVABILITY · Wallet example

### AI Tooling
AI_FIRST_WORKFLOW · FEATURE_SLICE_WORKFLOW · EVENT_MODELING · EVENT_MODEL_FORMAT ·
embabel-codegen/README · templates/README · templates/crablet-app/README

Also give the two runtime paths equal weight in the README body:
- `## Framework Path` (currently a single short paragraph titled "Manual Runtime Path")
- `## AI-First Path` (currently the dominant section; keep content, just label it clearly)

---

## Out of scope

- Module READMEs (`crablet-*/`) — none link to the moved files (confirmed by audit); rg will catch any exceptions.
- `docs/examples/` and `docs/tutorials/` — no changes.
- `docs/plans/**` — plan files are historical records; stale links in them are acceptable and excluded from the verification gate.
- No content edits inside moved files beyond fixing relative paths.
- Published site / crablet.app (if any) — separate deployment concern.

---

## Verification

### 1. Zero stale references (must be true before committing)

Re-run Check 1 and Check 2 from Step 2a. Both must produce zero hits.

### 2. docs-check passes (primary gate)

```bash
make docs-check
```

This runs `scripts/verify-docs.sh`, which validates all Markdown link targets including the newly added `docs/ai-tooling/EVENT_MODELING.md`. A passing run confirms both inbound and outbound link correctness.

### 3. Spot-check forward links manually

Open and visually verify a sample of links in:
- `README.md` documentation section
- `embabel-codegen/README.md`
- `templates/crablet-app/README.md`
