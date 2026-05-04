# AI Skills

Crablet ships Claude Code skills that describe how an AI agent should work with the project. They
are concise routing units: each skill covers one kind of work and points the agent toward the right
files, workflow, and constraints.

The generated app template includes the app-facing subset of these skills under
`.claude/skills/`. The framework repository also includes maintainer-focused skills for runtime and
codegen work.

## Main Skills

| Skill | Use it for |
|-------|------------|
| `/crablet-greenfield` | Start a new Crablet app: baseline setup, Event Modeling workshop, first generated slice, and later app evolution. |
| `/event-modeling` | Facilitate Event Modeling workshop dialogue and turn the conversation into generator-ready `event-model.yaml`. |
| `/crablet-codegen` | Configure codegen providers, run plan/generate, manage artifact ownership, and recover from generation or repair failures. |
| `/crablet-app-dev` | Build one generated application slice at a time: commands, handlers, views, automations, outbox, and verification. |
| `/dcb` | Choose or diagnose command consistency patterns: `idempotent`, `commutative`, `non-commutative`, tags, and `guardEvents`. |
| `/crablet-k8s` | Map the `deployment:` model to local Kubernetes manifests, KEDA settings, and singleton worker topology. |
| `/crablet-local-dev` | Handle local build, Testcontainers, LISTEN/NOTIFY constraints, MCP codegen loop, and troubleshooting. |
| `/crablet-diagram-advisor` | Work with event-model diagram metadata, actor boards, sidecar overlays, and renderer rules. |

## Maintainer Skills

| Skill | Use it for |
|-------|------------|
| `/crablet-maintainer` | Framework module changes, public API work, eventstore/commands/poller internals, templates, codegen internals, and maintainer docs. |
| `/kubernetes-skill` | Generic Kubernetes manifest, Helm, RBAC, and security hardening work outside Crablet-specific topology. |
| `/balanced-coupling` | Evaluate module coupling and classify balanced versus unbalanced dependencies. |
| `/design` | Produce modular architecture designs from functional requirements. |
| `/review` | Review modularity using the Balanced Coupling model. |
| `/document` | Produce modularity review documents in Markdown and HTML. |
| `/jspecify-skill` | Add or review JSpecify nullability support in Java modules. |

## Where They Live

- Repository skills: `/.claude/skills/`
- Generated app skills: `/templates/crablet-app/.claude/skills/`
- Repository routing hub: [CLAUDE.md](../../../CLAUDE.md)
- Generated app routing hub: [templates/crablet-app/CLAUDE.md](../../../templates/crablet-app/CLAUDE.md)

For a new application, start with `/crablet-greenfield`. For an already-scoped slice, start with
`/event-modeling`, then use `/crablet-codegen` or the Makefile targets to plan and generate, and
`/crablet-app-dev` to finish or customize the slice.
