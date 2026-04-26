# Future notes (non-binding)

Informal items for maintainers: not a committed roadmap. Promote real work to `docs/dev/plans/`, issues, or the appropriate module when ready.

## Backlog ideas

- [ ] Opt `docs-samples`, `crablet-test-support`, `shared-examples-domain`, and `wallet-example-app` into the Checkstyle import-style rule after deciding whether sample/support projects should follow the same build gate as core modules.

## Exploratory spikes

- **MiniStack + ECS (Fargate-shaped):** Time-boxed try with [MiniStack](https://github.com/ministackorg/ministack) (or similar) and a minimal ECS task definition / service layout mirroring Crablet’s “command API vs singleton worker” split. Outcome: document what emulates well vs what needs a real AWS account; informs a possible second deployment generator alongside Kubernetes.

- **Terraform (or CDK) against a local endpoint:** Optional follow-up: `terraform apply` with `endpoints` / `AWS_ENDPOINT_URL` pointed at the emulator for the same stack—only if the first spike is promising.

The Kubernetes path (`k8s` in embabel-codegen) remains the primary portable story; AWS-shaped deployment is a potential complement, not a replacement.
