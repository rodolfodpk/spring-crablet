# Plan: Document Crablet Codegen MCP Workflow

**Status: EXECUTED**

## Context

Gap analysis shows most of the intended documentation already exists:

- `root README.md` line 43 already says "The template's `.claude/settings.json` wires
  `embabel-codegen` as an MCP server. You never call `java -jar` directly."
- `templates/crablet-app/CLAUDE.md` already has all behavioral rules (ask for facts,
  update model first, run `embabel_plan`, wait for approval).
- `embabel-codegen/README.md` already documents MCP mode, the exposed tools, and
  `.claude/settings.json` discovery.

**The actual gaps** were in the **template READMEs** and **`templates/crablet-app/CLAUDE.md`**
(missing or misleading MCP and CLI framing), not in the root README or `embabel-codegen` docs.

1. `templates/crablet-app/README.md` — `.claude/settings.json` was **never mentioned**,
   and the "Local Commands" section presented `make plan`/`make generate` without framing
   them as fallback paths rather than the primary workflow.

2. `templates/README.md` — `.claude/settings.json` is already named in "What's included",
   but the "Workflow" block immediately below it led with `make plan`/`make generate`,
   which contradicted the MCP framing. The fix is reordering, not adding new content.

3. **MCP output path** — `embabel_generate` defaults **`output` to `.`** (project root), while
   `make generate` runs `generate --output src/main/java`. Docs must state that Claude should call
   **`embabel_generate` with `output: "src/main/java"`** for this template, or generated sources
   land in the wrong tree. **`templates/crablet-app/CLAUDE.md`** is updated to require that
   argument explicitly.

4. **CI wording** — `make generate` runs the **AI pipeline** (agents, compile, repair), so it is
   **not** a normal deterministic CI step. **`make plan`** (no Anthropic) and **`make verify`**
   are CI-friendly; **`make generate`** is for **manual or scripted regeneration** when you
   intentionally re-run codegen, or for **debugging** — not described as generic "CI fallback."

---

## Files to Change

### 1. `templates/crablet-app/README.md`

Add a "How Code Generation Works" section after the "First Slice" example dialogue (before
`## Local Commands`) explaining:

- Update the short workflow at the top of the README so the `embabel_generate` step also says
  **`output: "src/main/java"`**. This is the first workflow readers see, so it must not contradict
  the detailed MCP output-path warning later in the file.
- `.claude/settings.json` starts `java -jar ./tools/embabel-codegen.jar --mcp` when
  Claude Code opens the project.
- This exposes `embabel_plan`, `embabel_generate`, `embabel_init`, and `embabel_k8s` as
  MCP tools Claude Code calls automatically.
- In the recommended Claude Code workflow, users do not run `java -jar` directly.
- **`embabel_generate` must be called with `output` set to `src/main/java`** — same as
  `make generate`. The MCP tool’s default output is `.`; without this, files are written to
  the wrong place.
- Show the actual JSON snippet from `.claude/settings.json` for transparency.
- **Local commands:** `make plan` and `make verify` are suitable for **scripted checks** and
  **CI** (plan is deterministic, no Anthropic; verify is Maven). `make generate` is for **manual
  or scripted regeneration** when you intentionally run the AI pipeline (same as MCP
  `embabel_generate`), or for **debugging** — not the default interactive path and not a typical
  CI step. **`make k8s`** matches MCP **`embabel_k8s`**: **deterministic** Kubernetes manifest
  generation (no Anthropic), parallel to the CLI `k8s` command.

### 2. `templates/README.md`

Reframe the workflow block: Claude Code + MCP as primary; manual Makefile commands as secondary.
Include that **`embabel_generate` uses `output: src/main/java`** in this template. Manual section:
`make plan` / `make verify` = CI- or script-friendly; `make generate` = intentional AI regeneration
(same pipeline as `embabel_generate`), not standard CI; list **`make k8s`** alongside
**`embabel_k8s`** (no Anthropic).

Also update the "What's included" Makefile bullet so it lists **`k8s`** with the other targets.
Otherwise the workflow block documents `make k8s` while the inventory omits it.

### 3. `templates/crablet-app/CLAUDE.md`

Require **`embabel_generate` with `output` `src/main/java`** in the feature-slice steps (MCP
default `.` would misplace generated sources relative to this template and `make generate`).

---

## Files NOT Changing

| File | Reason |
|------|--------|
| `root README.md` | MCP explanation already correct at line 43 |
| `embabel-codegen/README.md` | MCP section already complete; default `output` is documented there |
| `.claude/settings.json` | Content correct; only documented for humans in template README |

---

## Executed changes

- **`docs/dev/plans/document-crablet-codegen-mcp-workflow.md`** — Plan text updated (output path, CI
  wording, verification run); status set to EXECUTED; this section and verification table added.
- **`templates/crablet-app/README.md`** — "The workflow" opener lists **`embabel_generate` with
  `output: "src/main/java"`**; added **How code generation works** (`.claude/settings.json` JSON,
  tool list, **`embabel_generate` default vs `src/main/java`**, primary vs local); reframed
  **Local commands**; documented **`make k8s` ↔ `embabel_k8s`** (deterministic, no Anthropic).
- **`templates/README.md`** — **Workflow (primary)** is MCP-first with **`embabel_generate` +
  `src/main/java`**; **Manual or scripted** lists `make plan` / `make generate` / `make k8s` /
  `make verify` with CI vs AI-generate framing; **What's included** Makefile bullet includes **`k8s`**.
- **`templates/crablet-app/CLAUDE.md`** — Step instructs **`embabel_generate` with `output`
  `src/main/java`** (MCP default `.` misplaces sources for this template).

## Verification (run from repository root, after the changes, not before)

Use **`rg`** if available; otherwise **`grep -E`** (or plain **`grep`**) on the same patterns.

```bash
# .claude/settings.json documented in template app README
rg "settings.json" templates/crablet-app/README.md
# grep "settings.json" templates/crablet-app/README.md

# embabel_generate output path documented (matches make generate)
rg "src/main/java" templates/crablet-app/README.md templates/README.md templates/crablet-app/CLAUDE.md
# grep "src/main/java" templates/crablet-app/README.md templates/README.md templates/crablet-app/CLAUDE.md

# templates/README.md: primary workflow is MCP-first, not make plan
head -n 35 templates/README.md

# make plan / make generate framed as manual or CI-appropriate, not "primary"
rg "fallback|manual|regenerat|CI" templates/crablet-app/README.md templates/README.md
# grep -E "fallback|manual|regenerat|CI" templates/crablet-app/README.md templates/README.md

# make k8s / embabel_k8s mapping documented
rg "embabel_k8s|make k8s" templates/crablet-app/README.md templates/README.md
# grep -E "embabel_k8s|make k8s" templates/crablet-app/README.md templates/README.md

# top-level "The workflow" in crablet-app README includes output: "src/main/java" on generate
sed -n '1,20p' templates/crablet-app/README.md

# templates "What's included" Makefile bullet includes k8s
rg "Makefile.*k8s" templates/README.md
# grep "Makefile" templates/README.md  # should show plan, generate, k8s, verify, check
```

**Manual check:** In `templates/README.md`, section **Workflow (primary — Claude Code + MCP)**, the
**first** steps must be **Open Claude Code** / `event-model.yaml` / **`embabel_*`** — not
`make plan` / `make generate`. Grep/keyword checks are a weak signal; read the section.

## Verification run

Last run: from **repository root** (clone of **spring-crablet**), after the template and plan
updates. Re-run the commands above and refresh this table when you change the docs.

| Check | Result |
|--------|--------|
| `settings.json` / `.claude/settings` appears in **How code generation works** (`templates/crablet-app/README.md`) | **Pass** |
| `src/main/java` in `templates/crablet-app/README.md`, `templates/README.md`, `templates/crablet-app/CLAUDE.md` | **Pass** |
| **Workflow (primary)** in `templates/README.md` leads with `Open Claude Code` / MCP / `embabel_*`, not `make plan` first | **Pass** — `make plan` only under **Manual or scripted** |
| CI / manual / regeneration wording in both template READMEs | **Pass** |
| `embabel_k8s` and `make k8s` in both template READMEs | **Pass** — **Kubernetes** paragraph in `crablet-app`; `make k8s` in `templates` manual list |
| **Manual read** of **Workflow (primary)** in `templates/README.md` | **Pass** — does not start with `make plan` / `make generate` |
| **The workflow** code block at top of `templates/crablet-app/README.md` includes `output` for `embabel_generate` | **Pass** — aligned with **How code generation works** |
| **What's included** in `templates/README.md` — Makefile targets include **`k8s`** | **Pass** |

The recorded run used **`grep`** and **`head`** (same intent as **`rg`** in the script). Update
**Last run** when re-verifying.
