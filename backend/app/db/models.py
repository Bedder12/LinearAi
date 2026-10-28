from datetime import datetime
from uuid import UUID

from sqlalchemy import DateTime, ForeignKey, Integer, JSON, String, UniqueConstraint, func
from sqlalchemy.dialects.postgresql import UUID as PGUUID
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


class WorkSession(Base):
    __tablename__ = "work_sessions"

    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True)
    accepted_revision: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    changes: Mapped[list["InkChange"]] = relationship(back_populates="session")


class InkChange(Base):
    __tablename__ = "ink_changes"
    __table_args__ = (UniqueConstraint("work_session_id", "revision", "position"),)

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    work_session_id: Mapped[UUID] = mapped_column(
        PGUUID(as_uuid=True), ForeignKey("work_sessions.id", ondelete="CASCADE"), nullable=False
    )
    revision: Mapped[int] = mapped_column(Integer, nullable=False)
    position: Mapped[int] = mapped_column(Integer, nullable=False)
    payload: Mapped[dict] = mapped_column(JSON, nullable=False)
    session: Mapped[WorkSession] = relationship(back_populates="changes")
