from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError

from app.api.analysis import router as analysis_router
from app.api.problems import request_validation_problem

app = FastAPI(
    title="Linear AI API",
    version="0.1.0",
)

app.include_router(analysis_router)
app.add_exception_handler(RequestValidationError, request_validation_problem)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}
