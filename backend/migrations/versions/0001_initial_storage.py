"""Create work sessions and ordered ink events."""

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0001_initial_storage"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "work_sessions",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("accepted_revision", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_table(
        "ink_changes",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("work_session_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("revision", sa.Integer(), nullable=False),
        sa.Column("position", sa.Integer(), nullable=False),
        sa.Column("payload", sa.JSON(), nullable=False),
        sa.ForeignKeyConstraint(["work_session_id"], ["work_sessions.id"], ondelete="CASCADE"),
        sa.UniqueConstraint("work_session_id", "revision", "position"),
    )
    op.create_index(
        "ix_ink_changes_session_revision",
        "ink_changes",
        ["work_session_id", "revision"],
    )


def downgrade() -> None:
    op.drop_index("ix_ink_changes_session_revision", table_name="ink_changes")
    op.drop_table("ink_changes")
    op.drop_table("work_sessions")
