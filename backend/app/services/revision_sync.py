from collections import defaultdict

from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app.db.models import InkChange, WorkSession
from app.schemas.analysis import AnalysisRequestPayload


class RevisionConflict(Exception):
    pass


class RevisionGap(Exception):
    pass


class InvalidInkChange(Exception):
    pass


def apply_revisions(db: Session, request: AnalysisRequestPayload) -> int:
    """Apply contiguous complete revisions atomically; commit before acknowledging."""
    if request.revision < 0:
        raise InvalidInkChange("revision must be nonnegative")

    grouped = defaultdict(list)
    last = -1
    for item in request.changes:
        if item.revision < 1 or item.revision < last:
            raise InvalidInkChange("change revisions must be positive and sorted")
        grouped[item.revision].append(item.change.model_dump(mode="json"))
        last = item.revision

    with db.begin():
        # INSERT ... ON CONFLICT makes concurrent first requests create only one session.
        db.execute(
            insert(WorkSession)
            .values(id=request.workSessionId, accepted_revision=0)
            .on_conflict_do_nothing(index_elements=[WorkSession.id])
        )
        session = db.scalar(
            select(WorkSession)
            .where(WorkSession.id == request.workSessionId)
            .with_for_update()
        )
        if session is None:
            raise RuntimeError("work session could not be loaded")

        accepted = session.accepted_revision
        if grouped and max(grouped) > request.revision:
            raise InvalidInkChange("changes exceed request revision")

        new_revisions = sorted(rev for rev in grouped if rev > accepted)
        if new_revisions:
            expected = list(range(accepted + 1, request.revision + 1))
            if new_revisions != expected:
                raise RevisionGap("missing revision(s)")
        elif request.revision > accepted:
            raise RevisionGap("missing revision(s)")

        # Every repeated event must match stored content, not just the revision number.
        for revision in sorted(r for r in grouped if r <= accepted):
            stored = db.scalars(
                select(InkChange)
                .where(InkChange.work_session_id == request.workSessionId)
                .where(InkChange.revision == revision)
                .order_by(InkChange.position)
            ).all()
            if [event.payload for event in stored] != grouped[revision]:
                raise RevisionConflict("conflicting retry")

        # Reconstruct current stroke identities to enforce basic event semantics.
        # Deliberately not an in-memory global; state is reconstructed from committed history.
        stored_events = db.scalars(
            select(InkChange)
            .where(InkChange.work_session_id == request.workSessionId)
            .order_by(InkChange.revision, InkChange.position)
        ).all()
        stroke_ids: set[str] = set()
        for event in stored_events:
            change = event.payload
            if change["type"] == "stroke_added":
                stroke_ids.add(change["stroke"]["id"])
            elif change["type"] == "stroke_removed":
                stroke_ids.discard(change["strokeId"])
            elif change["type"] == "document_cleared":
                stroke_ids.clear()

        for revision in new_revisions:
            for position, change in enumerate(grouped[revision]):
                typ = change["type"]
                if typ == "stroke_added":
                    stroke_id = change["stroke"]["id"]
                    if stroke_id in stroke_ids:
                        raise InvalidInkChange("stroke ID already exists")
                    stroke_ids.add(stroke_id)
                elif typ == "stroke_removed":
                    stroke_id = change["strokeId"]
                    if stroke_id not in stroke_ids:
                        raise InvalidInkChange("stroke does not exist")
                    stroke_ids.remove(stroke_id)
                else:
                    stroke_ids.clear()
                db.add(
                    InkChange(
                        work_session_id=request.workSessionId,
                        revision=revision,
                        position=position,
                        payload=change,
                    )
                )

        if new_revisions:
            session.accepted_revision = request.revision
            db.flush()

        return session.accepted_revision
