PREFS = {"job_types": ["stage"], "locations": [{"city": "Tunis", "country": "Tunisie"}],
         "remote_work": True, "threshold": 80, "notifications_enabled": False}


def test_valeurs_par_defaut(client, headers):
    r = client.get("/preferences", headers=headers).json()
    assert r["threshold"] == 70 and len(r["job_types"]) == 4


def test_enregistrer_puis_relire(client, headers):
    assert client.put("/preferences", json=PREFS, headers=headers).status_code == 200
    r = client.get("/preferences", headers=headers).json()
    assert r["threshold"] == 80 and r["job_types"] == ["stage"] and r["locations"][0]["city"] == "Tunis"


def test_seuil_entre_50_et_90(client, headers):
    for bad in (45, 95):
        assert client.put("/preferences", json={**PREFS, "threshold": bad}, headers=headers).status_code == 422


def test_au_moins_un_type_d_offre(client, headers):
    assert client.put("/preferences", json={**PREFS, "job_types": []}, headers=headers).status_code == 422


def test_reinitialiser(client, headers):
    client.put("/preferences", json=PREFS, headers=headers)
    assert client.delete("/preferences", headers=headers).status_code == 204
    assert client.get("/preferences", headers=headers).json()["threshold"] == 70
