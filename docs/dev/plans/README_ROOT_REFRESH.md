# Root README refresh

## Goal

Make `README.md` scannable in under ~90 seconds for first-time visitors while **preserving** all substantive content (move long sections into focused docs, don’t delete information).

## Design decisions (from review)

- Root README: elevator pitch, two paths (AI / manual runtime), one compact doc index, minimal duplication.
- Long **Module Boundaries** table belongs in a dedicated doc, not the landing page.
- Full `IdempotentCommandHandler` sample can shrink to a pointer + link (full example stays in tutorials / example app / module READMEs).
- Merge **“Goal | Read”** (early) and **“Where To Go Next”** into a single, grouped documentation index, or keep one table and remove overlapping bullets.

## Recommended order

**Run this plan before** [`k8s-generation-from-event-model.md`](k8s-generation-from-event-model.md) K8s doc work: it gives a cleaner doc tree (`docs/MODULES.md`, unified index) for cross-links from generated K8s docs and `DEPLOYMENT_TOPOLOGY.md`.

## Implementation steps

1. **Add `docs/MODULES.md`**
   - Move the current **“Modules”** compact table and the full **“Module Boundaries”** table from `README.md` here.
   - Short intro: required core vs optional add-ons, one sentence on `crablet-event-poller` as transitive infra.
   - Optional: one paragraph on write path vs read/side-effect path (the prose currently under the big table in README).
   - **Link only (do not copy prose):** for poller-backed module **deployment constraints**, in **`docs/MODULES.md`** use same-directory form: `[Deployment Topology](DEPLOYMENT_TOPOLOGY.md)`. One line is enough; do not duplicate the topology rules in `MODULES.md`.

2. **Slim `README.md`**
   - Keep: title, badges, **Why Crablet May Be Useful**, **When Crablet Fits**, **Why Java 25** (or fold Java 25 into a single line if space matters).
   - **AI-First Workflow**: keep one-time setup + “describe one outcome” + Claude steps; keep the **Goal | Read** table (or replace with merged index from step 3).
   - **Manual Runtime Path**: replace the long Java block with 2–3 sentences and **root-relative links** (from repository root, as used in GitHub and local clones):

     | Link text (example) | Path |
     |---------------------|------|
     | Create a Crablet app | `docs/CREATE_A_CRABLET_APP.md` |
     | Tutorial | `docs/TUTORIAL.md` |
     | Commands module | `crablet-commands/README.md` |
     | Wallet example | `wallet-example-app/README.md` |
     | Module reference | `docs/MODULES.md` |

   - **Learning And Deployment**: keep as short bullets; no table.
   - **Modules**: 2–3 sentences + **link to [Module reference](docs/MODULES.md)**; remove inlined tables.
   - **DCB At A Glance**: keep the small 3-row table + links to `crablet-eventstore/docs/`.
   - **License**: unchanged.

3. **Single documentation index**
   - Combine early navigation and **Where To Go Next** into one section, e.g. `## Documentation`, with subsections **Start**, **Build & operate**, **Architecture** — each 3–6 links max. In **`README.md` (repo root)**, add **`docs/PUBLIC_API.md`** (public HTTP / command API; when to use custom controllers) and **`docs/MODULES.md`** so links stay root-relative and correct on GitHub.

4. **Contributors (optional, one line in README)**

   - From **repo root `README.md`**, use: `[Build](docs/BUILD.md)` and `[CLAUDE.md](CLAUDE.md)` — not `../` paths (those are wrong for a root `README.md` on GitHub).

5. **Incoming links and anchors (mandatory)**
   - **Before merge:** run `rg 'README\.md#'` (and `rg 'README\.md' docs/`) from the repository root. Any `README.md` or `README.md#...` target that no longer exists after the edit must be **updated** in the referring file. External links that cannot be changed may use a short redirect note in `README.md` only as a last resort.

## Verification

- Read `README.md` start-to-finish: first screen states what Crablet is and where to go next without scrolling past two full screens of tables.
- `docs/MODULES.md` contains the full former module tables + prose; no information loss vs previous README; **topology** is link-only, not duplicated.
- `docs/PUBLIC_API.md` is linked from the new documentation index.
- All `README.md` and `#anchor` references from `docs/` and other consumers resolve after the change.

## Out of scope

- Rewriting `CLAUDE.md` or per-module READMEs (unless a link needs fixing).
- Changing CI or badge URLs.

## Relation to other plans

- **[`docs/PUBLIC_API.md`](../PUBLIC_API.md)** exists; the README index should link to it (no “future” placeholder).
- **[`k8s-generation-from-event-model.md`](k8s-generation-from-event-model.md)** (K8s codegen): after this refresh lands, that plan adds **`docs/MODULES.md`** to its documentation story so generated K8s is discoverable from the module doc; it keeps **`docs/DEPLOYMENT_TOPOLOGY.md`** platform-agnostic and links in.
