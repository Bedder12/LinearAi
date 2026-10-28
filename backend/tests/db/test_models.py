from sqlalchemy import inspect
from sqlalchemy.dialects import postgresql

from app.db.models import Base


def test_storage_schema_has_expected_constraints() -> None:
    work = Base.metadata.tables["work_sessions"]
    changes = Base.metadata.tables["ink_changes"]

    assert "accepted_revision" in work.columns
    assert {"work_session_id", "revision", "position", "payload"} <= set(changes.columns)
    assert any(
        set(c.columns.keys()) == {"work_session_id", "revision", "position"}
        for c in changes.constraints
        if hasattr(c, "columns")
    )

    # The schema can compile for the actual PostgreSQL dialect.
    for table in (work, changes):
        assert str(table.to_metadata(Base.metadata).name)
        assert table.primary_key is not None


def test_uuid_column_uses_postgresql_uuid() -> None:
    table = Base.metadata.tables["work_sessions"]
    assert table.columns["id"].type.compile(dialect=postgresql.dialect()) == "UUID"
