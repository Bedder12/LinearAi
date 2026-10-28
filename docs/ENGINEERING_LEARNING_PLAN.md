# Linear AI — Engineering and Infrastructure Learning Plan

Updated: 2026-10-28

## Purpose
Build Linear AI as a working Android-first mathematics tutoring application while acquiring transferable software engineering skills relevant to Swedish backend, cloud, and AI engineering positions. **Prioritize product correctness over collecting technologies.**

This is a decision guide, not proof that all components are implemented.

## Current verified baseline (at time of writing)
- Android client: Kotlin, Jetpack Compose, AndroidX Ink; vector ink, incremental revision transport.
- Backend: Python 3.12+, FastAPI, Pydantic, `/health` and `/analysis/check`.
- Analysis service currently acknowledges revisions; persistent state, mathematical recognition and verification are not yet implemented.
- Separate Android and backend GitHub Actions workflows.
- Docker backend support proposed in PR #1 on `feat/docker-backend`; Docker CI and backend CI passed, but the PR had not yet merged when this document was drafted.
- No evidence that PostgreSQL or cloud deployment is provisioned.

## Technology choices and rationale

| Area | Preferred direction | Justification / condition |
|---|---|---|
| Source control & automation | GitHub + GitHub Actions | Already used; build on existing CI rather than migrate to GitLab without a need |
| Backend | Python + FastAPI + Pydantic | Existing implementation and future AI analysis support |
| API distribution | Docker image | Reproducible runtime across CI and cloud; no requirement for Docker on the Windows development PC |
| Local multi-service work | Docker Compose | For integration tests and local development when Docker is available |
| Database | PostgreSQL | Sessions, ordered revisions, ink events, and later analysis results |
| Database access / migrations | SQLAlchemy 2 + Alembic (evaluate when implementing) | Explicit transactions, schema evolution, testability |
| First cloud platform to explore | Azure | Relevant skill signal in sampled Swedish job ads; learn resource groups, IAM, networking, deployment, billing and monitoring |
| Cloud backend deployment | Azure Container Apps (candidate) | Run the backend container without managing Kubernetes |
| Cloud database | Managed PostgreSQL, provider and tier decided after pricing review | Avoid paid idle resources; Neon may be a lower-cost early development alternative |
| Observability | Structured logs, health checks, metrics, traces when needed | Diagnose errors and provide operational evidence |
| Infra as Code | Terraform or Azure Bicep after first working deployment | Recreate infrastructure reliably; choose one to avoid tool sprawl |
| AI engineering | Recognition → structured math representation → deterministic verification → minimal hints | Product's central learning and quality requirements |

## Implementation order

1. **Stabilize container foundation.** Review and merge Docker PR #1 after CI checks; preserve port 8080 and Android's existing contract. Learn Dockerfile, images, containers, networks, permissions, health checks.
2. **Design persistence and revision invariants before deployment.** Define work sessions, ink changes, revision ordering, idempotency, conflicts/gaps, atomic commits, and what an acknowledgment guarantees. Keep raw vector ink as source of truth.
3. **Implement PostgreSQL and migrations.** Add test database via Compose/service containers; avoid exposing PostgreSQL publicly. Prove persistence across restarts and correct concurrency behavior.
4. **Test systematically.** Python unit and API tests, database integration tests, Docker smoke tests, Android contract tests, and eventually model evaluation suites. Ordinary CI should not call billable AI models.
5. **Deploy a development environment.** Compare Azure Container Apps, managed database options and costs first; use HTTPS, dedicated secrets, least-privilege identities, and a deliberate access strategy. Do not expose student data publicly without authentication/authorization.
6. **Improve delivery and operations.** CI to versioned image, registry, deployment approval, logs, rollout and rollback. Add monitoring and IaC after confirming functional value.
7. **Add AI analysis carefully.** Recognition and math evaluation baselines before adding agents, RAG, specialized workers or model fine-tuning.
8. **Learn Kubernetes separately.** Pods, Deployments, Services, Secrets, probes and rollouts in an isolated lab; adopt for the application only if real scaling/availability requirements justify it.

## Things NOT to add by default
- Kubernetes/AKS, service mesh, microservices or multiple cloud providers.
- Redis, message brokers, workers or vector databases before a concrete workload needs them.
- A self-hosted GitLab server or privileged CI runners.
- Public PostgreSQL ports or unrestricted AI endpoints.
- Production-like always-on paid resources before budget review.

## Employment-market interpretation
A review of Swedish job advertisements discussed on 2026-10-28 suggested CI/CD, Azure, Docker, Kubernetes, SQL and cloud operations are recurring skill signals. The cited samples included experienced and platform roles and were not a representative census of junior jobs. **Do not treat sample percentages or one employer's stack as a guarantee of hiring demand.** Revalidate market evidence periodically when choosing what to learn next. Broadly transferable skills (software design, SQL, testing, networking, security, CI/CD) outrank familiarity with any one hosting UI.

## Learning approach
For every new topic, explain: (1) what problem led to it, (2) prerequisite concepts and terminology, (3) how it works, (4) one runnable example, (5) where Linear AI uses it, (6) trade-offs and alternatives, (7) how to verify learning and correct behavior. Do not assume prior knowledge of Docker, kernels, VMs, Kubernetes or cloud infrastructure.

## Working constraints
- The Windows desktop is the Android development machine. An unrelated Linux homelab discussed earlier belongs to another person and must not be treated as this project's infrastructure.
- Available alternatives include Chromebooks, an ASUS machine, and Macs; exact specifications have not been verified.
- Cloud hosting is a candidate, not yet selected or deployed. Control financial exposure and secrets before provisioning.
- This file is an evolving plan; update it based on code, verified tests, costs and actual job evidence.
