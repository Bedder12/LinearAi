"""RFC 9457 Problem Details responses for HTTP API failures."""

from fastapi import Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel


class ProblemDetails(BaseModel):
    type: str
    title: str
    status: int
    detail: str
    acceptedRevision: int | None = None


def problem_response(
    *, status: int, kind: str, title: str, detail: str,
    accepted_revision: int | None = None,
) -> JSONResponse:
    problem = ProblemDetails(
        type=f"/problems/{kind}",
        title=title,
        status=status,
        detail=detail,
        acceptedRevision=accepted_revision,
    )
    return JSONResponse(
        status_code=status,
        media_type="application/problem+json",
        content=problem.model_dump(exclude_none=True),
    )


async def request_validation_problem(
    _request: Request, exc: RequestValidationError
) -> JSONResponse:
    # Do not serialize Pydantic input values: they may contain sensitive user data.
    count = len(exc.errors())
    return problem_response(
        status=422,
        kind="request-validation",
        title="Invalid request",
        detail=f"Request validation failed ({count} issue(s)).",
    )
