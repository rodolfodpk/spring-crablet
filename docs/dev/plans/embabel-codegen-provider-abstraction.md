# Embabel-Based Multi-Provider Codegen

## Summary

Move `crablet-codegen` off direct Anthropic-only wiring and onto an Embabel-backed,
provider-agnostic completion layer. Keep existing CLI/MCP command names stable, add Cursor MCP
template support, and update docs so users understand the difference between their coding frontend
and the generator's LLM provider.

Provider support should cover named hosted providers, local runtimes such as Ollama, and custom
OpenAI-compatible endpoints. Relevant docs to update or cross-link: `crablet-codegen/README.md`,
`docs/user/ai-tooling/`, and `templates/crablet-app/README.md`.

## Implementation Changes

- First do an Embabel integration spike:
  - verify Maven coordinates/version for the Embabel LLM abstraction
  - confirm Java 25 + current Spring Boot compatibility
  - confirm fat-JAR packaging impact with provider SDKs
  - identify the exact API used for prompt-in/text-out completion
  - check dependency size, license, BOM alignment, and whether provider dependencies should be
    optional/profile-based
- Introduce a narrow codegen port, e.g. `CodegenLlmClient.complete(systemPrompt, userPrompt)`, backed
  by Embabel.
  - Agents depend only on this port or the stable Embabel interface.
  - Provider adapters may reference Anthropic/OpenAI SDK types; generator agents must not.
- Replace direct injections in `CommandsAgent`, `EventsAgent`, `ViewsAgent`, `AutomationsAgent`,
  `OutboxAgent`, and `RepairAgent`.
- Preserve existing public surfaces:
  - CLI: `plan`, `generate`, `init`, `k8s`, `--mcp`
  - MCP tools: `crablet_plan`, `crablet_generate`, `crablet_init`, `crablet_k8s`
- Make config backward-compatible:
  - keep `codegen.anthropic.*` and `ANTHROPIC_API_KEY` working for existing users
  - add provider-neutral config such as `codegen.llm.provider`, mapped via Spring relaxed binding to
    env vars like `CODEGEN_LLM_PROVIDER`
  - add named provider config through Embabel for Anthropic, OpenAI, DeepSeek, and other supported
    hosted providers
  - add custom OpenAI-compatible endpoint config with `base-url`, `model`, and optional/dummy
    `api-key` so local Ollama, LM Studio, vLLM, LiteLLM, and similar gateways can be used
  - support Ollama either through Embabel/Spring AI Ollama integration or through Ollama's
    OpenAI-compatible API at a local base URL such as `http://localhost:11434/v1`
  - emit provider-aware missing-key errors
- Update user-visible strings in `McpServer`, `CodegenCommand`, and docs from "Anthropic" to
  model/provider-neutral wording where appropriate.
- Add `templates/crablet-app/.cursor/mcp.json` after validating Cursor's current project MCP schema,
  using the same local JAR pattern: `java -jar ./tools/crablet-codegen.jar --mcp`.

## Documentation Changes

- Update root README, `crablet-codegen/README.md`, AI tooling docs, and template docs to separate:
  - frontend: Claude Code, Cursor, Codex, terminal
  - provider: Anthropic, OpenAI, DeepSeek, Ollama, or another configured Embabel-supported or
    OpenAI-compatible provider
- Document:
  - Claude Code: existing `.claude/settings.json`
  - Cursor: new `.cursor/mcp.json`
  - Codex/other agents: edit `event-model.yaml`, run `make plan`, then `make generate`
  - terminal/manual: same Makefile/CLI flow
- Replace universal `ANTHROPIC_API_KEY` prerequisites with provider-specific setup.
- Document local provider setup with examples for Ollama/OpenAI-compatible endpoints, including
  `CODEGEN_LLM_PROVIDER`, `base-url`, `model`, and API-key behavior when the backend ignores keys.
- Keep `plan` documented as deterministic and model-free.

## Test Plan

- Add a wiring test that forbids Anthropic SDK or `AnthropicService` references in generator agent
  constructors/imports, while allowing them inside provider adapter classes.
- Add config tests for:
  - default/backward-compatible Anthropic path
  - OpenAI provider selection
  - DeepSeek or another named OpenAI-compatible hosted provider selection
  - local OpenAI-compatible endpoint selection without requiring `ANTHROPIC_API_KEY` or
    `OPENAI_API_KEY`
  - unknown provider failure
  - missing provider key failure with the right env/config name
- Keep existing parser, planner, MCP, and k8s tests green.
- Run the canonical codegen checks:
  - confirm whether `make codegen-check` is preferred over raw Maven
  - run `cd crablet-codegen && ../mvnw test` if still the module-local test command
  - run `make docs-check`
- Ensure codegen tests and docs checks remain part of PR validation.

## Assumptions

- Embabel's LLM abstraction is available from Maven with an API stable enough for this generator.
- Existing Claude Code users must not need to change command names or MCP tool names.
- `codegen.anthropic.*` remains supported as a backward-compatible alias/config path.
- Cursor support is MCP-first; Codex support is CLI/Makefile-first for this pass.
- Local-provider quality depends on the selected model's ability to follow strict file-block output
  instructions; docs should recommend capable coding models rather than implying every local model
  will generate compiling Java.
