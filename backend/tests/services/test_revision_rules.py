from uuid import UUID

import pytest

from app.schemas.analysis import AnalysisRequestPayload
from app.services.revision_rules import (
    InvalidInkChange,
    RevisionConflict,
    RevisionGap,
    active_stroke_ids,
    apply_stroke_rule,
    group_changes,
    new_revisions_for,
    validate_retry,
)


def request(revision, entries):
    return AnalysisRequestPayload.model_validate({
        "workSessionId": str(UUID(int=1)),
        "revision": revision,
        "changes": [
            {"revision": rev, "change": change}
            for rev, change in entries
        ],
    })


def clear():
    return {"type": "document_cleared"}


def add(stroke_id):
    return {"type": "stroke_added", "stroke": {
        "id": stroke_id, "toolType": "pen", "points": [],
    }}


def test_groups_changes_without_losing_order():
    grouped = group_changes(request(2, [(1, add("a")), (1, add("b")), (2, clear())]))
    assert [item["stroke"]["id"] for item in grouped[1]] == ["a", "b"]
    assert grouped[2] == [clear()]


@pytest.mark.parametrize("entries", [
    [(0, clear())],
    [(2, clear()), (1, clear())],
])
def test_invalid_revision_order(entries):
    with pytest.raises(InvalidInkChange):
        group_changes(request(2, entries))


def test_gap_and_valid_retry():
    assert new_revisions_for({1: [clear()], 2: [clear()]}, 0, 2) == [1, 2]
    assert new_revisions_for({1: [clear()]}, 1, 1) == []
    with pytest.raises(RevisionGap):
        new_revisions_for({2: [clear()]}, 0, 2)
    with pytest.raises(RevisionGap):
        new_revisions_for({}, 1, 2)
    with pytest.raises(InvalidInkChange):
        new_revisions_for({3: [clear()]}, 0, 2)


def test_retry_checks_full_content():
    validate_retry([clear()], [clear()])
    with pytest.raises(RevisionConflict):
        validate_retry([clear()], [add("a")])


def test_rebuild_and_apply_stroke_semantics():
    state = active_stroke_ids([
        add("a"), add("b"),
        {"type": "stroke_removed", "strokeId": "a"},
    ])
    assert state == {"b"}
    with pytest.raises(InvalidInkChange):
        apply_stroke_rule(state, add("b"))
    with pytest.raises(InvalidInkChange):
        apply_stroke_rule(state, {"type": "stroke_removed", "strokeId": "missing"})
    apply_stroke_rule(state, add("c"))
    assert state == {"b", "c"}
    apply_stroke_rule(state, clear())
    assert state == set()
