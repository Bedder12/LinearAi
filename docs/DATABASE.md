# Development database foundation

This change prepares PostgreSQL persistence, but deliberately does **not** modify `POST /analysis/check` to acknowledge persisted changes. The current endpoint still acknowledges requests without storage. It must not be advertised as durable synchronization.

## Start the optional local database

Docker Compose base stack remains unchanged. For PostgreSQL development, create an untracked root `.env` containing:

```text
POSTGRES_PASSWORD=<replace-with-a-strong-development-password>
```

Do not commit this file or reuse a production password.

From the repository root:

```bash
docker compose -f compose.yaml -f compose.postgres.yaml up --build -d
```

The database is not published on the host. Within the Docker network, the backend can reach `db:5432`.

For migrations, run inside the backend container:

```bash
docker compose -f compose.yaml -f compose.postgres.yaml exec backend alembic upgrade head
```

The first migration creates `work_sessions` and `ink_changes`. Each ink event is ordered by `(work_session_id, revision, position)` because multiple events can belong to one logical revision. SQLAlchemy models live in `app/db/models.py`.

## Important next implementation

Add a storage-backed revision application service which:
- Locks the work session row while checking and updating its revision.
- Handles initial revision, sequential changes, duplicate retries and revision gaps explicitly.
- Applies all events in a revision in one transaction and updates accepted_revision only after commit.
- Detects conflicting retries; a revision number alone is not proof that a retry has identical content.
- Validates change ordering and stroke semantics; reconstructs server-side state.
- Tests rollback, concurrency and persistence across restarts with a real PostgreSQL database.
- Preserves existing Android payload/response compatibility where possible.

No public database port, user authentication, cloud deployment, or extra worker is added by this change.
