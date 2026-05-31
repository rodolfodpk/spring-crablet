# Claude Code hooks

Project-shared hooks wired in `.claude/settings.json`. They shift two documented,
mechanical rules left — from CI into the editing session — without running Maven
(hooks stay fast static checks only).

| Hook | Event | Behavior |
|------|-------|----------|
| `guard-mvn-pl.sh` | `PreToolUse` / `Bash` | **Blocks** `mvnw … -pl` without `-am` (stale-SNAPSHOT footgun). Lets `make …` and `-am`/`--also-make` through. |
| `guard-banned-strings.sh` | `PostToolUse` / `Edit`\|`Write` | **Warns** (never blocks) when an edited file contains a banned term (currently `embabel`). |

## Advisory unless `jq` is installed

Both hooks parse the tool-call JSON with `jq`. If `jq` is not on `PATH` they exit 0
(no-op) so they can never wedge developer flow. With `jq` absent the guards are
effectively disabled — CI remains the real gate. Install `jq` to get edit-time enforcement.

## Adding a banned term

Edit `BANNED_TERMS` in `guard-banned-strings.sh` (case-insensitive extended regex,
e.g. `embabel|legacyname`).
