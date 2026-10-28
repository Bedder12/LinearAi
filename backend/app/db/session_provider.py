from functools import lru_cache

from sqlalchemy.orm import Session, sessionmaker

from app.db.connection import create_database_engine, create_session_factory


@lru_cache(maxsize=1)
def get_session_factory() -> sessionmaker[Session]:
    return create_session_factory(create_database_engine())
