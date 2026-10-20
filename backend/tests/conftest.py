import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.core.config import settings
from app.db.base import Base
from app.db.session import get_db
from app.main import app

if not settings.test_database_url:
    pytest.exit("TEST_DATABASE_URL manquant dans le .env : les tests ne tournent jamais sur la base de dev.")

engine = create_engine(settings.test_database_url)
TestingSession = sessionmaker(bind=engine, expire_on_commit=False)


@pytest.fixture()
def db():
    Base.metadata.drop_all(engine)
    Base.metadata.create_all(engine)
    with TestingSession() as session:
        yield session
    Base.metadata.drop_all(engine)


@pytest.fixture()
def client(db):
    def override_get_db():
        with TestingSession() as session:
            yield session

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()


def register(client, email="nour@example.com", password="motdepasse123", consent=True):
    return client.post("/auth/register", json={"email": email, "password": password, "language": "fr", "consent": consent})


def login(client, email="nour@example.com", password="motdepasse123"):
    return client.post("/auth/login", data={"username": email, "password": password})
