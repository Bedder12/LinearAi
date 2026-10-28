from uuid import UUID

import pytest

from app.schemas.analysis import AnalysisRequestPayload
from app.services.revision_sync import InvalidInkChange, RevisionGap


def test_request_contract_parses_events() -> None:
    request = AnalysisRequestPayload.model_validate({
        "workSessionId": str(UUID(int=1)),
        "revision": 1,
        "changes": [{"revision": 1, "change": {"type": "document_cleared"}}],
    })
    assert request.changes[0].change.type == "document_cleared"


def test_rejects_reversed_revision_order_before_database_access() -> None:
    request = AnalysisRequestPayload.model_validate({
        "workSessionId": str(UUID(int=1)),
        "revision": 2,
        "changes": [
            {"revision": 2, "change": {"type": "document_cleared"}},
            {"revision": 1, "change": {"type": "document_cleared"}},
        ],
    })
    from app.services.revision_sync import apply_revisions
    with pytest.raises(InvalidInkChange):
        apply_revisions(None, request)
