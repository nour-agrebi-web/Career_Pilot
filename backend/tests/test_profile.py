from tests.conftest import auth_headers


def test_profil_cree_vide_au_premier_acces(client, headers):
    r = client.get("/profile", headers=headers)
    assert r.status_code == 200
    assert r.json()["skills"] == [] and r.json()["validated"] is False


def test_profil_exige_une_connexion(client):
    assert client.get("/profile").status_code == 401


def test_crud_competence(client, headers):
    created = client.post("/profile/skills", json={"name": "Kotlin"}, headers=headers)
    assert created.status_code == 201
    skill_id = created.json()["id"]

    assert [s["name"] for s in client.get("/profile/skills", headers=headers).json()] == ["Kotlin"]

    updated = client.put(f"/profile/skills/{skill_id}", json={"name": "Kotlin Android"}, headers=headers)
    assert updated.json() == {"id": skill_id, "name": "Kotlin Android"}

    assert client.delete(f"/profile/skills/{skill_id}", headers=headers).status_code == 204
    assert client.get("/profile/skills", headers=headers).json() == []
    assert client.delete(f"/profile/skills/{skill_id}", headers=headers).status_code == 404


def test_competence_en_double_refusee(client, headers):
    client.post("/profile/skills", json={"name": "Python"}, headers=headers)
    assert client.post("/profile/skills", json={"name": " python "}, headers=headers).status_code == 409


def test_experience_dates_incoherentes_refusees(client, headers):
    r = client.post("/profile/experiences", headers=headers, json={
        "position": "Stagiaire", "company": "Nexa", "start_date": "2026-06-01", "end_date": "2026-01-01"})
    assert r.status_code == 422
    assert isinstance(r.json()["detail"], str)


def test_section_inconnue(client, headers):
    assert client.post("/profile/hobbies", json={"name": "x"}, headers=headers).status_code == 422


def test_modifier_domaine_et_niveau(client, headers):
    r = client.put("/profile", json={"domain": "Développement mobile", "level": "stage"}, headers=headers)
    assert r.json()["domain"] == "Développement mobile" and r.json()["level"] == "stage"
    assert client.put("/profile", json={"level": "expert"}, headers=headers).status_code == 422


def test_validation_exige_un_profil_exploitable(client, headers):
    assert client.post("/profile/validate", headers=headers).status_code == 422
    client.post("/profile/skills", json={"name": "Kotlin"}, headers=headers)
    assert client.post("/profile/validate", headers=headers).status_code == 422  # ni formation ni expérience
    client.post("/profile/educations", headers=headers, json={
        "degree": "Licence DSI", "institution": "ISET Radès", "start_date": "2024-09-15"})
    r = client.post("/profile/validate", headers=headers)
    assert r.status_code == 200 and r.json()["validated"] is True


def test_profils_cloisonnes(client, headers):
    skill_id = client.post("/profile/skills", json={"name": "Kotlin"}, headers=headers).json()["id"]
    other = auth_headers(client, email="amine@example.com")
    assert client.get("/profile/skills", headers=other).json() == []
    assert client.delete(f"/profile/skills/{skill_id}", headers=other).status_code == 404
