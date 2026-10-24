import pytest
from fastapi.testclient import TestClient
from app.core.config import settings
from app.db.mongo import client as mongo_client, create_indexes, get_db
from app.main import app

# Les tests utilisent une base séparée (careerpilot_test), vidée avant et après chaque test.
TEST_DB = settings.mongodb_test_db
assert TEST_DB != settings.mongodb_db, "La base de test doit être différente de la base de dev."


@pytest.fixture()
def db():
    mongo_client.drop_database(TEST_DB)
    database = mongo_client[TEST_DB]
    create_indexes(database)
    yield database
    mongo_client.drop_database(TEST_DB)


@pytest.fixture()
def client(db):
    app.dependency_overrides[get_db] = lambda: db
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()


def register(client, email="nour@example.com", password="motdepasse123", consent=True):
    return client.post("/auth/register", json={"email": email, "password": password, "language": "fr", "consent": consent})


def login(client, email="nour@example.com", password="motdepasse123"):
    return client.post("/auth/login", data={"username": email, "password": password})
