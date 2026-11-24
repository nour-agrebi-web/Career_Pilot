from pymongo.errors import ServerSelectionTimeoutError
from app.db.mongo import get_db
from app.main import app


def test_health_checks_mongodb(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "ok", "mongodb": "ok"}


class DownDb:
    """Base injoignable : toute opération échoue comme pymongo quand MongoDB est arrêté."""

    def __getattr__(self, name):
        def fail(*args, **kwargs):
            raise ServerSelectionTimeoutError("localhost:27017: connection refused")
        if name == "command":
            return fail
        return type("Col", (), {"__getattr__": lambda self, _: fail})()


def test_mongodb_down_returns_clear_503(client):
    app.dependency_overrides[get_db] = lambda: DownDb()
    assert client.get("/health").status_code == 503
    r = client.post("/auth/register", json={"email": "a@b.tn", "password": "motdepasse123", "language": "fr", "consent": True})
    assert r.status_code == 503
    assert "MongoDB" in r.json()["detail"]
