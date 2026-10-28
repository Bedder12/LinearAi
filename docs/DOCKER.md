# Docker development

The Android application is built with Gradle outside Docker. This setup containers only the Python FastAPI backend.

## Requirements
- Docker Engine with Docker Compose v2, or Docker Desktop configured for Linux containers.

From the repository root:

```bash
docker compose up --build --wait -d
curl http://127.0.0.1:8080/health
docker compose logs -f backend
docker compose down
```

FastAPI documentation: http://127.0.0.1:8080/docs

The host port is bound to loopback only. For the Android emulator, `10.0.2.2:8080` routes to the development host (subject to emulator networking). A physical Android device needs a deliberately configured reachable address, appropriate firewall rules, and secure transport before external exposure.

The container runs as an unprivileged user, includes a health check, and uses the host Linux kernel. No database or AI worker is required for the current acknowledgement-only service.

The build installs from `pyproject.toml`. Dependencies are currently not pinned, so rebuilds are not fully reproducible. Introduce an appropriate resolved lock/constraints strategy before treating images as production release artifacts.

The CI workflow validates Compose, builds the backend image, starts it, and checks `/health`. It does not publish images or deploy the application.
