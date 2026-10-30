"""Orchestrate atomic revision synchronization for an ink work session."""

from sqlalchemy.orm import Session

from app.db.revision_repository import (
    add_change,
    get_or_create_session_for_update,
    load_committed_events,
    load_revision_events,
)
from app.schemas.analysis import AnalysisRequestPayload
from app.services.revision_rules import (
    InvalidInkChange as InvalidInkChange,
    RevisionConflict as RevisionConflict,
    RevisionGap as RevisionGap,
    active_stroke_ids,
    apply_stroke_rule,
    group_changes,
    new_revisions_for,
    validate_retry,
)

# Keep the exceptions and apply_revisions public from this module for existing API callers.


def apply_revisions(db: Session, request: AnalysisRequestPayload) -> int:
    """Apply contiguous complete revisions atomically; commit before acknowledging."""
    grouped = group_changes(request)

    with db.begin():
        session = get_or_create_session_for_update(db, request.workSessionId)
        accepted = session.accepted_revision
        new_revisions = new_revisions_for(grouped, accepted, request.revision)

        # Verify retries against committed history before processing new changes.
        for revision in sorted(rev for rev in grouped if rev <= accepted):
            stored = load_revision_events(db, request.workSessionId, revision)
            validate_retry(stored, grouped[revision])

        # Keep reconstructing from committed history until a measured need for
        # a materialized stroke state justifies more persistence complexity.
        stroke_ids = active_stroke_ids(
            load_committed_events(db, request.workSessionId)
        )

        for revision in new_revisions:
            for position, change in enumerate(grouped[revision]):
                apply_stroke_rule(stroke_ids, change)
                add_change(db, request.workSessionId, revision, position, change)

        if new_revisions:
            session.accepted_revision = request.revision
            db.flush()

        return session.accepted_revision
