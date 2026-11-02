from tests.conftest import auth_headers


def upload(client, headers, name="cv.pdf", content=b"%PDF-1.4 contenu"):
    return client.post("/cvs", files={"file": (name, content, "application/pdf")}, headers=headers)


def test_import_d_un_cv(client, headers, storage):
    r = upload(client, headers)
    assert r.status_code == 201
    assert r.json()["version"] == 1 and r.json()["active"] is True and r.json()["status"] == "UPLOADED"
    assert any(storage.rglob("*.pdf"))


def test_format_et_taille(client, headers, storage):
    assert upload(client, headers, name="cv.png").status_code == 415
    assert upload(client, headers, content=b"x" * (5 * 1024 * 1024 + 1)).status_code == 413


def test_un_seul_cv_actif_et_activation(client, headers, storage):
    first = upload(client, headers).json()
    second = upload(client, headers, name="cv2.docx").json()
    cvs = client.get("/cvs", headers=headers).json()
    assert [cv["version"] for cv in cvs] == [2, 1]
    assert [cv["active"] for cv in cvs] == [True, False]

    client.put(f"/cvs/{first['id']}/activate", headers=headers)
    assert client.get(f"/cvs/{first['id']}", headers=headers).json()["active"] is True
    assert client.get(f"/cvs/{second['id']}", headers=headers).json()["active"] is False


def test_suppression_du_cv_actif(client, headers, storage):
    upload(client, headers)
    second = upload(client, headers).json()
    assert client.delete(f"/cvs/{second['id']}", headers=headers).status_code == 204
    cvs = client.get("/cvs", headers=headers).json()
    assert len(cvs) == 1 and cvs[0]["active"] is True


def test_dix_versions_au_maximum(client, headers, storage):
    for _ in range(12):
        upload(client, headers)
    versions = [cv["version"] for cv in client.get("/cvs", headers=headers).json()]
    assert versions == list(range(12, 2, -1))


def test_cv_d_un_autre_utilisateur_introuvable(client, headers, storage):
    cv_id = upload(client, headers).json()["id"]
    other = auth_headers(client, email="amine@example.com")
    assert client.get(f"/cvs/{cv_id}", headers=other).status_code == 404
    assert client.get("/cvs/pas-un-id", headers=headers).status_code == 404
