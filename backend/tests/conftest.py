import pytest
from fastapi.testclient import TestClient
from app.core.config import settings
from app.db.mongo import client as mongo_client, create_indexes, get_db
from app.main import app

# Les tests utilisent une base séparée (careerpilot_test), vidée avant et après chaque test.
TEST_DB = settings.mongodb_test_db
assert TEST_DB != settings.mongodb_db, "La base de test doit être différente de la base de dev."


def fake_embed(texts, settings=None):
    """Embeddings factices et déterministes (8 dimensions) à la place d'Ollama."""
    import hashlib
    return [[b / 255 for b in hashlib.sha256(t.encode()).digest()[:8]] for t in texts]


@pytest.fixture(autouse=True)
def no_external_ai(monkeypatch):
    # Les tests n'ont besoin ni d'Ollama ni d'un dossier Qdrant : analyse par règles, Qdrant en mémoire.
    from app.modules.cv import indexing
    from app.vector import store
    monkeypatch.setattr(indexing, "embed_documents", fake_embed)
    monkeypatch.setattr(settings, "ai_engine", "regles")
    monkeypatch.setattr(settings, "qdrant_url", "")
    monkeypatch.setattr(settings, "qdrant_path", ":memory:")
    store.reset_client()
    yield
    store.reset_client()


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


def auth_headers(client, email="nour@example.com"):
    register(client, email=email)
    token = login(client, email=email).json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture()
def headers(client):
    return auth_headers(client)


@pytest.fixture()
def storage(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "storage_dir", str(tmp_path))
    return tmp_path
