# PostgreSQL revision persistence

`POST /analysis/check` applies ink revisions through PostgreSQL-backed storage and returns an acknowledgment only after the transaction commits. The endpoint acknowledges committed ink history; it does not perform mathematical analysis, provide authenticated cloud backup, or protect the Android client's in-memory pending queue from process death.

PostgreSQL integration tests require a migrated database configured through `DATABASE_URL`. They skip when that test database is unavailable, so a passing test run without `DATABASE_URL` does not verify database persistence.

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

## Current persistence behavior

- The revision service locks the work-session row and applies changes within one transaction.
- Revisions must be contiguous; identical retries are checked against committed event content, while gaps, conflicting retries, and invalid stroke operations are rejected.
- Accepted revision and ordered ink events are committed before the API returns `acceptedRevision`.
- The Android request/response contract remains compatible with the existing client.

## Remaining verification and limitations

- Run the integration tests with a migrated PostgreSQL test database to verify persistence, retries, conflicts, gaps, and transaction behavior.
- Verify the complete Android-to-FastAPI-to-PostgreSQL flow, including network failures and in-flight edits.
- The API does not authenticate or authorize work-session owners. A UUID is an identifier, not access control.
- The Android pending change journal is held in ViewModel memory and is not durable across process death.
- No cloud deployment, public database port, or background worker is provided.
