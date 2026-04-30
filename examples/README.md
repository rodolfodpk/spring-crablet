# Examples

Two runnable Spring Boot applications demonstrating different Crablet patterns.

| App | Port | What it shows |
|-----|------|---------------|
| [wallet-example-app](wallet-example-app/README.md) | 8080 | Single-aggregate patterns: open, deposit, withdraw, transfer. Full management UI. |
| [course-example-app](course-example-app/README.md) | 8081 | Multi-entity DCB: one command enforces course capacity + student limit in a single consistency boundary. |

Both apps are built separately after `make install`. See [BUILD.md](../docs/user/BUILD.md) for instructions.
