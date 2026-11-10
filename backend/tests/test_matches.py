from seeds.offers_dev import seed_offers
from app.modules.offers.matching import score


def build_profile(client, headers):
    for skill in ("Kotlin", "Android", "MVVM", "Retrofit", "Python", "FastAPI"):
        client.post("/profile/skills", json={"name": skill}, headers=headers)
    client.post("/profile/educations", headers=headers, json={
        "degree": "Licence développement des systèmes d'information", "institution": "ISET Radès",
        "start_date": "2024-09-15"})
    client.post("/profile/languages", json={"name": "Français", "level": "C1"}, headers=headers)
    client.put("/profile", json={"domain": "Développement mobile Android", "level": "stage"}, headers=headers)
    client.post("/profile/validate", headers=headers)


def test_refresh_exige_un_profil_valide(client, headers):
    assert client.post("/matches/refresh", headers=headers).status_code == 409


def test_cycle_de_matching(client, headers, db):
    seed_offers(db)
    build_profile(client, headers)
    client.put("/preferences", headers=headers, json={"job_types": ["stage", "emploi", "freelance"],
                                                      "locations": [{"city": "Tunis"}], "threshold": 50})
    assert client.post("/matches/refresh", headers=headers).json()["new_matches"] > 0

    matches = client.get("/matches", headers=headers).json()
    scores = [m["score"] for m in matches]
    assert scores == sorted(scores, reverse=True) and min(scores) >= 50
    best = matches[0]
    assert best["offer"]["title"].startswith("Stage PFE – Développeur Android")
    assert "Docker" in best["missing_skills"]
    assert sum(best["breakdown"].values()) == best["score"]
    # Filtres éliminatoires : Sousse et Lyon sont hors zone, le temps partiel n'est pas souhaité.
    assert {m["offer"]["location"]["city"] for m in matches}.isdisjoint({"Sousse", "Lyon"})
    assert all(m["offer"]["type"] != "temps_partiel" for m in matches)

    # Relever le seuil masque les offres en dessous (US-3.2.4).
    high = client.get("/matches", params={"min_score": best["score"]}, headers=headers).json()
    assert all(m["score"] >= best["score"] for m in high)


def test_sauvegarder_et_masquer(client, headers, db):
    seed_offers(db)
    build_profile(client, headers)
    client.put("/preferences", headers=headers, json={"job_types": ["stage", "freelance"], "threshold": 50})
    client.post("/matches/refresh", headers=headers)
    first, second = client.get("/matches", headers=headers).json()[:2]

    saved = client.put(f"/matches/{first['id']}", json={"status": "ACCEPTED"}, headers=headers)
    assert saved.json()["status"] == "ACCEPTED"
    assert [m["id"] for m in client.get("/matches", params={"status": "ACCEPTED"}, headers=headers).json()] == [first["id"]]

    assert client.delete(f"/matches/{second['id']}", headers=headers).status_code == 204
    # Une offre masquée n'est plus proposée, même après un nouveau cycle.
    client.post("/matches/refresh", headers=headers)
    assert second["id"] not in [m["id"] for m in client.get("/matches", headers=headers).json()]


def test_offre_hors_type_eliminee():
    profile = {"skills": [{"name": "Python"}], "languages": [], "level": "junior"}
    prefs = {"job_types": ["stage"], "locations": [], "remote_work": True}
    offer = {"title": "Dev", "type": "emploi", "required_skills": ["Python"], "location": {"city": "Tunis"}}
    assert score(profile, prefs, offer) is None
    assert score(profile, {**prefs, "job_types": ["emploi"]}, offer).breakdown["skills"] == 30
