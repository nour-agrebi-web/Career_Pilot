"""Vectorisation des offres (Qdrant en mémoire, embeddings factices) et similarité sémantique dans le score."""
from datetime import datetime, timezone
from app.core.config import settings
from app.modules.offers import indexing
from app.modules.offers.matching import keyword_ratio, score, semantic_ratio
from app.vector import store
from seeds.offers_dev import OFFERS, seed_offers
from tests.conftest import fake_embed
from tests.test_matches import build_profile

# Embeddings « thématiques » : deux textes qui parlent de mobile sont proches même sans mot commun.
MOBILE = ("android", "kotlin", "mobile", "smartphone")
FINANCE = ("comptab", "fiscal", "finance")


def topic_vector(text: str) -> list[float]:
    low = text.lower()
    return [float(sum(w in low for w in MOBILE)), float(sum(w in low for w in FINANCE)), 0.05]


def topic_documents(texts, cfg=None):
    return [topic_vector(t) for t in texts]


def topic_query(text, cfg=None):
    return topic_vector(text)


def offer_points():
    client = store.get_client(settings)
    return client.scroll(store.OFFERS, limit=100)[0] if client.collection_exists(store.OFFERS) else []


def add_offer(db, **fields):
    offer = {"source": "Keejob", "source_id": fields["title"], "company": "ACME", "type": "stage",
             "location": {"city": "Tunis", "country": "Tunisie"}, "work_mode": "sur_site", "level": "stage",
             "language": None, "required_skills": [], "description": "", "status": "ACTIVE",
             "publication_date": datetime.now(timezone.utc), "url": "https://www.keejob.com/", **fields}
    return db.offers.insert_one(offer).inserted_id


def test_texte_et_empreinte_de_l_offre():
    offer = {"title": "Dev Android", "required_skills": ["Kotlin"], "level": "stage", "company": "Nexa",
             "description": "Application   mobile\nnative."}
    text = indexing.offer_text(offer)
    assert text == "Offre : Dev Android. Compétences : Kotlin. Niveau : stage. Application mobile native."
    fp = indexing.fingerprint(offer, "nomic-embed-text")
    assert fp == indexing.fingerprint({**offer, "company": "Autre"}, "nomic-embed-text")  # hors texte vectorisé
    assert fp != indexing.fingerprint({**offer, "description": "Autre chose"}, "nomic-embed-text")
    assert fp != indexing.fingerprint(offer, "autre-modele")
    assert indexing.offer_text({"title": None, "description": None}) == "Offre : ."


def test_seules_les_offres_nouvelles_ou_modifiees_sont_vectorisees(db, monkeypatch):
    calls = []
    monkeypatch.setattr(indexing, "embed_documents", lambda texts, cfg: calls.append(texts) or fake_embed(texts))
    seed_offers(db)
    assert indexing.index_offers(db) == {"vectorisees": len(OFFERS), "inchangees": 0, "retirees": 0, "restantes": 0}
    points = offer_points()
    assert len(points) == len(OFFERS)
    ids = {str(o["_id"]) for o in db.offers.find()}
    assert {p.payload["offer_id"] for p in points} == ids
    assert {p.payload["source"] for p in points} == {o["source"] for o in OFFERS}
    assert all(p.payload["embed_model"] == settings.ollama_embed_model for p in points)

    # Deuxième passage sans changement : aucun appel à Ollama, aucun doublon.
    assert indexing.index_offers(db)["vectorisees"] == 0 and len(calls) == 1 and len(offer_points()) == len(OFFERS)

    # Une offre modifiée (par le scraper) : elle seule est revectorisée, à la même place dans Qdrant.
    db.offers.update_one({"source_id": "tj-1001"}, {"$set": {"description": "Nouvelle description",
                                                             "content_hash": "abc"}})
    assert indexing.index_offers(db)["vectorisees"] == 1
    assert len(calls[-1]) == 1 and "Nouvelle description" in calls[-1][0] and len(offer_points()) == len(OFFERS)

    # Une offre expirée est retirée de Qdrant.
    db.offers.update_one({"source_id": "ft-2002"}, {"$set": {"status": "EXPIRED"}})
    assert indexing.index_offers(db)["retirees"] == 1
    assert len(offer_points()) == len(OFFERS) - 1
    assert "vector_index" not in db.offers.find_one({"source_id": "ft-2002"})


def test_ollama_en_panne_au_milieu_reprend_au_run_suivant(db, monkeypatch):
    monkeypatch.setattr(indexing, "EMBED_BATCH", 2)
    batches = []

    def flaky(texts, cfg):
        batches.append(texts)
        if len(batches) == 2:
            raise RuntimeError("Ollama injoignable")
        return fake_embed(texts)

    monkeypatch.setattr(indexing, "embed_documents", flaky)
    seed_offers(db)
    try:
        indexing.index_offers(db)
    except RuntimeError:
        pass
    assert len(offer_points()) == 2              # le premier lot est enregistré
    stats = indexing.index_offers(db)
    assert stats["vectorisees"] == len(OFFERS) - 2 and len(offer_points()) == len(OFFERS)


def test_le_matching_utilise_la_similarite_des_embeddings(client, headers, db, monkeypatch):
    monkeypatch.setattr(indexing, "embed_documents", topic_documents)
    monkeypatch.setattr(indexing, "embed_query", topic_query)
    build_profile(client, headers)          # profil « Développement mobile Android », Kotlin...
    client.put("/preferences", headers=headers, json={"job_types": ["stage"], "threshold": 50})
    # Aucun mot en commun avec le profil, mais le même métier : seuls les embeddings le voient.
    phone = add_offer(db, title="Créateur d'applications smartphone")
    add_offer(db, title="Assistant comptable", description="Saisie comptable et déclarations fiscales.")

    response = client.post("/matches/refresh", headers=headers).json()
    assert response["semantic_engine"] == "embeddings"
    matches = client.get("/matches", headers=headers).json()
    assert [m["offer"]["id"] for m in matches] == [str(phone)]
    assert matches[0]["breakdown"]["semantic"] == 40 and sum(matches[0]["breakdown"].values()) == matches[0]["score"]
    assert len(offer_points()) == 2


def test_ollama_eteint_le_score_retombe_sur_les_mots_cles(client, headers, db):
    # conftest : Ollama absent par défaut.
    build_profile(client, headers)
    client.put("/preferences", headers=headers, json={"job_types": ["stage"], "threshold": 50})
    add_offer(db, title="Créateur d'applications smartphone")
    android = add_offer(db, title="Développeur Android", required_skills=["Kotlin", "Android"])

    response = client.post("/matches/refresh", headers=headers)
    assert response.status_code == 200 and response.json()["semantic_engine"] == "mots-cles"
    matches = client.get("/matches", headers=headers).json()
    assert [m["offer"]["id"] for m in matches] == [str(android)]
    profile = db.profiles.find_one()
    expected = score(profile, {"job_types": ["stage"], "locations": []}, db.offers.find_one({"_id": android}))
    assert matches[0]["breakdown"] == expected.breakdown


def test_qdrant_indisponible_repli_sans_erreur(client, headers, db, monkeypatch):
    build_profile(client, headers)
    monkeypatch.setattr(indexing, "embed_documents", topic_documents)
    monkeypatch.setattr(indexing, "embed_query", topic_query)

    def locked(cfg):
        raise RuntimeError("Storage folder storage/qdrant is already accessed by another instance")

    monkeypatch.setattr(store, "get_client", locked)
    seed_offers(db)
    response = client.post("/matches/refresh", headers=headers)
    assert response.status_code == 200 and response.json()["semantic_engine"] == "mots-cles"


def test_conversion_du_cosinus_et_offre_non_vectorisee():
    assert semantic_ratio(0.2) == 0 and semantic_ratio(0.9) == 1
    assert 0 < semantic_ratio(0.6) < 1
    profile = {"skills": [{"name": "Python"}], "languages": [], "level": "junior", "domain": "Data"}
    prefs = {"job_types": ["emploi"], "locations": [], "remote_work": True}
    offer = {"title": "Dev Python", "type": "emploi", "required_skills": ["Python"], "location": {"city": "Tunis"}}
    # Pas de similarité (offre pas encore vectorisée) : mots-clés, comme avant.
    assert score(profile, prefs, offer).breakdown["semantic"] == round(40 * keyword_ratio(profile, offer))
    assert score(profile, prefs, offer, similarity=0.9).breakdown["semantic"] == 40
    assert score(profile, prefs, offer, similarity=0.1).breakdown["semantic"] == 0


def test_actualisation_rapide_le_reste_vectorise_en_tache_de_fond(client, headers, db, monkeypatch):
    # Des centaines d'offres du scraper d'un coup : l'actualisation n'en vectorise qu'une partie (sinon l'app,
    # qui attend 30 s au plus, abandonnait la requête et n'affichait rien), le reste suit en tâche de fond.
    from app.modules.offers import router
    monkeypatch.setattr(router, "REFRESH_EMBED_BUDGET", 2)
    calls = []
    monkeypatch.setattr(indexing, "embed_documents", lambda t, cfg=None: calls.append(len(t)) or topic_documents(t))
    monkeypatch.setattr(indexing, "embed_query", topic_query)
    build_profile(client, headers)
    client.put("/preferences", headers=headers, json={"job_types": ["stage"], "threshold": 50})
    for i in range(5):
        add_offer(db, title=f"Développeur Android Kotlin {i}", required_skills=["Kotlin", "Android"])

    response = client.post("/matches/refresh", headers=headers).json()
    assert response["semantic_engine"] == "embeddings"
    assert response["offers_pending_embedding"] == 3
    assert len(client.get("/matches", headers=headers).json()) == 5       # aucune offre perdue entre-temps
    # TestClient exécute la tâche de fond après la réponse : tout est vectorisé ensuite.
    assert calls[0] == 2 and len(offer_points()) == 5
    assert client.post("/matches/refresh", headers=headers).json()["offers_pending_embedding"] == 0
