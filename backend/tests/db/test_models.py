from sqlalchemy.dialects import postgresql

from app.db.models import Base


def test_storage_schema_has_expected_constraints() -> None:
    work = Base.metadata.tables["work_sessions"]
    changes = Base.metadata.tables["ink_changes"]

    assert "accepted_revision" in work.columns
    assert {"work_session_id", "revision", "position", "payload"} <= set(changes.columns.keys())
    assert any(
        set(constraint.columns.keys()) == {"work_session_id", "revision", "position"}
        for constraint in changes.constraints
        if hasattr(constraint, "columns")
    )
    assert work.primary_key is not None
    assert changes.primary_key is not None


def test_uuid_column_uses_postgresql_uuid() -> None:
    table = Base.metadata.tables["work_sessions"]
    assert table.columns["id"].type.compile(dialect=postgresql.dialect()) == "UUID"
