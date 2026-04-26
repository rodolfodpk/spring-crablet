# Public API Guidance

Crablet keeps the generic command HTTP API intentionally small. It is useful for simple command
submission, tests, internal tools, and early adoption, but application-specific APIs should use
normal Spring controllers when HTTP semantics matter.

Use the generic command API when:

- clients can submit a JSON command with a `commandType`
- `201 Created`, `200 OK` for idempotent/no-op results, `400 Bad Request`, `404 Not Found`, and
  `409 Conflict` are enough
- the generic response body is enough for clients

Use custom `@RestController` endpoints when:

- the endpoint needs resource-specific paths, status codes, headers, validation, or OpenAPI detail
- the response needs domain data beyond command execution status
- the API is public-facing and should not expose generic command names as its contract

Reads should normally come from views, and integration events should normally leave the service via
outbox publishers. For the full generic command HTTP contract, see
[`crablet-commands-web/README.md`](../../crablet-commands-web/README.md).

The generic command API also supports opt-in UUID correlation headers. Enable
`crablet.commands.api.correlation-header-enabled=true` to accept and echo `X-Correlation-Id`
and store that UUID on appended events.
