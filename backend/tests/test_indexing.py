"""Vectorisation du profil validé dans Qdrant (en mémoire pour les tests) et client Ollama."""
import httpx
import pytest
from app.core.config import settings
from app.modules.cv import indexing
from app.vector import store
from tests.conftest import auth_headers, fake_embed


def fill_profile(client, headers, skills=("Kotlin", "Python")):
    for name in skills:
        client.post("/profile/skills", json={"name": name}, headers=headers)
    client.post("/profile/experiences", headers=headers, json={
        "position": "Stagiaire Android", "company": "Nexa", "start_date": "2025-02-01", "end_date": "2025-06-01"})
    client.post("/profile/educations", headers=headers, json={
        "degree": "Licence DSI", "institution": "ISET Rades", "start_date": "2023-09-01"})
    client.put("/profile", json={"domain": "Développement mobile", "level": "stage"}, headers=headers)


def user_id(db, email="nour@example.com"):
    return str(db.users.find_one({"email": email})["_id"])


def points(uid):
    client = store.get_client(settings)
    return client.scroll(store.CV_CHUNKS, scroll_filter=store._match(user_id=uid), limit=100)[0]


def test_build_chunks_decoupe_le_profil():
    profile = {"domain": "Data", "level": "junior", "languages": [{"name": "Anglais", "level": "C1"}],
               "skills": [{"name": "Python"}, {"name": "SQL"}],
               "experiences": [{"id": "e1", "position": "Analyste", "company": "Vermeg",
                                "start_date": "2024-01-01", "end_date": None}],
               "educations": [{"id": "f1", "degree": "Master", "institution": "ISG",
                               "start_date": "2022-09-01", "end_date": "2024-06-01"}]}
    chunks = indexing.build_chunks(profile)
    assert [c["section"] for c in chunks] == ["profil", "skills", "experiences", "educations"]
    assert chunks[1]["text"] == "Compétences : Python, SQL."
    assert chunks[2]["text"] == "Expérience : Analyste chez Vermeg (2024-01 – en cours)."


def test_validation_vectorise_le_profil_une_seule_fois(client, headers, db, monkeypatch):
    calls = []
    monkeypatch.setattr(indexing, "embed_documents", lambda texts, s: calls.append(texts) or fake_embed(texts))
    fill_profile(client, headers)
    assert client.post("/profile/validate", headers=headers).status_code == 200

    uid = user_id(db)
    stored = points(uid)
    assert len(stored) == 4 and len(calls) == 1
    assert {p.payload["section"] for p in stored} == {"profil", "skills", "experiences", "educations"}
    assert all(p.payload["cv_id"] == indexing.MANUAL and p.payload["user_id"] == uid for p in stored)
    state = client.get("/profile/vector-index", headers=headers).json()
    assert state["status"] == "INDEXED" and state["chunks"] == 4

    # Revalider sans rien changer : pas de nouvel appel à Ollama, pas de doublon.
    client.post("/profile/validate", headers=headers)
    assert len(calls) == 1 and len(points(uid)) == 4

    # Le profil change (nouvelle compétence) : les vecteurs sont remplacés, toujours sans doublon.
    client.post("/profile/skills", json={"name": "Docker"}, headers=headers)
    client.post("/profile/validate", headers=headers)
    assert len(calls) == 2 and len(points(uid)) == 4
    skills = next(p for p in points(uid) if p.payload["section"] == "skills")
    assert "Docker" in skills.payload["text"]


def test_vecteurs_cloisonnes_par_utilisateur(client, headers, db):
    fill_profile(client, headers)
    client.post("/profile/validate", headers=headers)
    other = auth_headers(client, email="amine@example.com")
    fill_profile(client, other, skills=("Java",))
    client.post("/profile/validate", headers=other)
    assert len(points(user_id(db))) == 4 and len(points(user_id(db, "amine@example.com"))) == 4
    # Recherche filtrée sur un utilisateur : jamais les morceaux d'un autre.
    hits = store.search(store.get_client(settings), store.CV_CHUNKS, fake_embed(["Compétences : Java."])[0],
                        user_id=user_id(db))
    assert hits and all(h.payload["user_id"] == user_id(db) for h in hits)


def test_ollama_eteint_la_validation_reussit_quand_meme(client, headers, db, monkeypatch):
    def down(texts, s):
        raise httpx.ConnectError("refusé")

    monkeypatch.setattr(indexing, "embed_documents", down)
    fill_profile(client, headers)
    assert client.post("/profile/validate", headers=headers).json()["validated"] is True
    state = client.get("/profile/vector-index", headers=headers).json()
    assert state["status"] == "ERROR" and "refusé" in state["error"]

    # Ollama revient : la validation suivante réessaie et réussit.
    monkeypatch.setattr(indexing, "embed_documents", fake_embed)
    client.post("/profile/validate", headers=headers)
    assert client.get("/profile/vector-index", headers=headers).json()["status"] == "INDEXED"


class FakeResponse:
    def __init__(self, status_code, data):
        self.status_code, self._data, self.text = status_code, data, str(data)

    def json(self):
        return self._data


def test_ollama_structurer_envoie_le_schema_json(monkeypatch):
    from app.modules.cv import structuring
    sent = {}

    def fake_post(url, json, timeout):
        sent.update(url=url, payload=json)
        content = '{"domain": "Mobile", "level": "stage", "skills": [{"name": "Kotlin"}], "experiences": null, ' \
                  '"educations": [], "languages": []}'
        return FakeResponse(200, {"message": {"content": content}})

    monkeypatch.setattr(httpx, "post", fake_post)
    monkeypatch.setattr(settings, "ai_engine", "ollama")
    draft, engine = structuring.structure_text("Compétences\nKotlin, Java", settings)
    assert engine == "ollama" and draft["skills"] == [{"name": "Kotlin"}] and draft["experiences"] == []
    assert sent["url"].endswith("/api/chat") and sent["payload"]["model"] == settings.ollama_model
    assert sent["payload"]["format"] == structuring.DRAFT_SCHEMA and sent["payload"]["stream"] is False


def test_modele_absent_message_clair(monkeypatch):
    from app.ai.ollama import OllamaClient, OllamaError
    monkeypatch.setattr(httpx, "post", lambda url, json, timeout: FakeResponse(404, {"error": "not found"}))
    with pytest.raises(OllamaError, match="ollama pull nomic-embed-text"):
        OllamaClient("http://localhost:11434", 5).embed("nomic-embed-text", ["texte"])
