"""Pure validation and state rules for revision synchronization."""

from collections import defaultdict

from app.schemas.analysis import AnalysisRequestPayload


class RevisionConflict(Exception):
    pass


class RevisionGap(Exception):
    def __init__(self, accepted_revision: int):
        self.accepted_revision = accepted_revision
        super().__init__("missing revision(s)")


class InvalidInkChange(Exception):
    pass


def group_changes(request: AnalysisRequestPayload) -> dict[int, list[dict]]:
    if request.revision < 0:
        raise InvalidInkChange("revision must be nonnegative")

    grouped: dict[int, list[dict]] = defaultdict(list)
    last = -1
    for item in request.changes:
        if item.revision < 1 or item.revision < last:
            raise InvalidInkChange("change revisions must be positive and sorted")
        grouped[item.revision].append(item.change.model_dump(mode="json"))
        last = item.revision
    return dict(grouped)


def new_revisions_for(
    grouped: dict[int, list[dict]], accepted: int, requested: int
) -> list[int]:
    if grouped and max(grouped) > requested:
        raise InvalidInkChange("changes exceed request revision")

    new_revisions = sorted(rev for rev in grouped if rev > accepted)
    if new_revisions:
        expected = list(range(accepted + 1, requested + 1))
        if new_revisions != expected:
            raise RevisionGap(accepted)
    elif requested > accepted:
        raise RevisionGap(accepted)
    return new_revisions


def validate_retry(stored: list[dict], incoming: list[dict]) -> None:
    if stored != incoming:
        raise RevisionConflict("conflicting retry")


def active_stroke_ids(events: list[dict]) -> set[str]:
    """Rebuild active stroke IDs from already committed event history."""
    stroke_ids: set[str] = set()
    for change in events:
        if change["type"] == "stroke_added":
            stroke_ids.add(change["stroke"]["id"])
        elif change["type"] == "stroke_removed":
            stroke_ids.discard(change["strokeId"])
        elif change["type"] == "document_cleared":
            stroke_ids.clear()
    return stroke_ids


def apply_stroke_rule(stroke_ids: set[str], change: dict) -> None:
    """Validate a new event and mutate the working set of active stroke IDs."""
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
