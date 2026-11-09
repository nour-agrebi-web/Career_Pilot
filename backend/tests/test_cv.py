from app.core.config import settings
from app.modules.cv import extraction
from app.modules.cv.anonymize import anonymize
from app.modules.cv.structuring import keep_grounded
from tests.conftest import auth_headers
from tests.cv_files import CV_TEXT, make_blank_pdf, make_docx, make_pdf

DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"


def upload(client, headers, name="cv.pdf", content=b"%PDF-1.4 contenu"):
    return client.post("/cvs", files={"file": (name, content, "application/pdf")}, headers=headers)


def test_import_d_un_cv(client, headers, storage):
    r = upload(client, headers)
    assert r.status_code == 201
    assert r.json()["version"] == 1 and r.json()["active"] is True and r.json()["status"] == "UPLOADED"
    stored = list(storage.rglob("*.enc"))
    assert len(stored) == 1
    assert b"%PDF" not in stored[0].read_bytes()  # chiffré au repos


def analysis(client, headers, cv_id):
    return client.get(f"/cvs/{cv_id}/analysis", headers=headers).json()


def test_analyse_d_un_pdf(client, headers, storage, db):
    cv = upload(client, headers, content=make_pdf()).json()
    # TestClient exécute la tâche de fond avant de rendre la main : l'analyse est terminée.
    result = analysis(client, headers, cv["id"])
    assert result["status"] == "PROCESSED" and result["extraction_method"] == "pdf" and result["engine"] == "regles"
    draft = result["draft"]
    assert [s["name"] for s in draft["skills"]] == ["Kotlin", "Jetpack Compose", "Python", "FastAPI", "MongoDB", "Git"]
    assert draft["experiences"] == [{"position": "Stagiaire développeuse", "company": "Nexa Digital",
                                     "start_date": "2025-07-01", "end_date": "2025-08-01"}]
    assert draft["educations"][0]["institution"] == "ISET Radès"
    assert {lang["name"]: lang["level"] for lang in draft["languages"]} == {"Arabe": "natif", "Français": "C1", "Anglais": "B2"}
    assert draft["level"] == "stage"
    assert result["missing_sections"] == []
    # Le texte extrait est chiffré en base, et le profil déduit ne contient aucune coordonnée.
    doc = db.cvs.find_one({})
    assert "Kotlin" not in doc["extracted_text"]
    assert "AGREBI" not in str(draft) and "@" not in str(draft) and "22 123 456" not in str(draft)


def test_analyse_d_un_docx(client, headers, storage):
    r = client.post("/cvs", files={"file": ("cv.docx", make_docx(), DOCX_TYPE)}, headers=headers)
    result = analysis(client, headers, r.json()["id"])
    assert result["status"] == "PROCESSED" and result["extraction_method"] == "docx"
    assert len(result["draft"]["skills"]) == 6 and len(result["draft"]["educations"]) == 2


def test_cv_corrompu_en_erreur(client, headers, storage):
    cv = upload(client, headers, content=b"%PDF-1.4 fichier tronque").json()
    result = analysis(client, headers, cv["id"])
    assert result["status"] == "ERROR" and result["error_code"] == "UNREADABLE"
    assert result["draft"] is None
    # US-2.2.3 : aucun profil n'est créé à partir d'un CV en erreur.
    assert client.post(f"/cvs/{cv['id']}/apply-profile", headers=headers).status_code == 409
    assert client.get("/profile", headers=headers).json()["skills"] == []


def test_faux_docx_en_erreur(client, headers, storage):
    r = client.post("/cvs", files={"file": ("cv.docx", b"pas une archive", DOCX_TYPE)}, headers=headers)
    assert analysis(client, headers, r.json()["id"])["error_code"] == "UNREADABLE"


def test_cv_scanne_sans_ocr(client, headers, storage, monkeypatch):
    monkeypatch.setattr(extraction, "ocr_available", lambda: False)
    cv = upload(client, headers, content=make_blank_pdf()).json()
    result = analysis(client, headers, cv["id"])
    assert result["status"] == "ERROR" and result["error_code"] == "NO_TEXT"


def test_cv_scanne_lu_par_ocr(client, headers, storage, monkeypatch):
    monkeypatch.setattr(extraction, "_ocr_pdf", lambda content: CV_TEXT)
    cv = upload(client, headers, content=make_blank_pdf()).json()
    result = analysis(client, headers, cv["id"])
    assert result["status"] == "PROCESSED" and result["extraction_method"] == "ocr"


def test_appliquer_le_profil_deduit(client, headers, storage):
    text = CV_TEXT.replace("Anglais (B2)", "Allemand")  # langue sans niveau : à compléter par le candidat
    cv = upload(client, headers, content=make_pdf(text)).json()
    r = client.post(f"/cvs/{cv['id']}/apply-profile", headers=headers)
    assert r.status_code == 200
    body = r.json()
    assert body["profile"]["validated"] is False and body["profile"]["level"] == "stage"
    assert len(body["profile"]["skills"]) == 6 and len(body["profile"]["educations"]) == 2
    assert body["incomplete_items"] == [{"section": "languages", "item": {"name": "Allemand", "level": None},
                                         "missing_fields": ["level"]}]
    assert client.get(f"/cvs/{cv['id']}", headers=headers).json()["applied"] is True
    # Le candidat peut ensuite valider son profil (au moins une compétence et une formation).
    assert client.post("/profile/validate", headers=headers).json()["validated"] is True


def test_sections_vides_signalees(client, headers, storage):
    text = "Profil\nJe suis passionnée par le développement et je cherche un premier stage.\nCompétences\nKotlin, Python"
    cv = upload(client, headers, content=make_pdf(text)).json()
    assert analysis(client, headers, cv["id"])["missing_sections"] == ["educations", "experiences"]


def test_quota_ia_de_cinq_par_jour(client, headers, storage):
    for i in range(5):
        assert upload(client, headers, content=make_pdf(CV_TEXT + f"\nVersion {i}")).status_code == 201
    assert client.get("/cvs/quota", headers=headers).json() == {"used": 5, "limit": 5, "remaining": 0}
    r = upload(client, headers, content=make_pdf(CV_TEXT + "\nVersion 6"))
    assert r.status_code == 429 and "5 opérations IA par jour" in r.json()["detail"]
    cvs = client.get("/cvs", headers=headers).json()
    assert len(cvs) == 5 and cvs[0]["version"] == 5 and cvs[0]["active"] is True  # rien n'a changé
    # Un fichier déjà analysé est repris du cache : pas de nouvelle opération IA.
    again = upload(client, headers, content=make_pdf(CV_TEXT + "\nVersion 0"))
    assert again.status_code == 201 and again.json()["status"] == "PROCESSED"
    assert client.post(f"/cvs/{cvs[0]['id']}/analyze", headers=headers).status_code == 429


def test_relancer_l_analyse(client, headers, storage):
    cv = upload(client, headers, content=make_pdf()).json()
    assert client.post(f"/cvs/{cv['id']}/analyze", headers=headers).status_code == 202
    assert analysis(client, headers, cv["id"])["status"] == "PROCESSED"
    assert client.get("/cvs/quota", headers=headers).json()["used"] == 2


def test_analyse_d_un_autre_utilisateur_introuvable(client, headers, storage):
    cv_id = upload(client, headers, content=make_pdf()).json()["id"]
    other = auth_headers(client, email="amine@example.com")
    assert client.get(f"/cvs/{cv_id}/analysis", headers=other).status_code == 404
    assert client.post(f"/cvs/{cv_id}/apply-profile", headers=other).status_code == 404
    assert client.post(f"/cvs/{cv_id}/analyze", headers=other).status_code == 404


def test_anonymisation():
    text = anonymize(CV_TEXT)
    for secret in ("AGREBI", "nour.agrebi@example.com", "22 123 456", "rue de la Liberté"):
        assert secret not in text
    assert "07/2025 - 08/2025" in text and "2024 - 2027" in text  # les dates ne sont pas prises pour des numéros
    assert "Kotlin" in text


def test_l_agent_n_invente_rien():
    draft = {"skills": [{"name": "Kotlin"}, {"name": "Docker"}],
             "experiences": [{"position": "Développeur", "company": "Google"}],
             "educations": [{"degree": "Licence DSI", "institution": "ISET Radès"}],
             "languages": [{"name": "Espagnol", "level": "B1"}]}
    kept = keep_grounded(draft, "Compétences : Kotlin. Formation : Licence DSI, ISET Rades")
    assert kept["skills"] == [{"name": "Kotlin"}]
    assert kept["experiences"] == [] and kept["languages"] == []
    assert len(kept["educations"]) == 1


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


def test_llm_invente_une_competence_retiree(client, headers, storage, monkeypatch):
    from app.modules.cv import structuring
    sent = {}

    def fake_structure(self, text):
        sent["text"] = text
        return {"domain": "Développement mobile", "level": "stage",
                "skills": [{"name": "Kotlin"}, {"name": "Docker"}], "experiences": [], "educations": [],
                "languages": []}

    monkeypatch.setattr(settings, "anthropic_api_key", "cle-de-test")
    monkeypatch.setattr(structuring.ClaudeStructurer, "structure", fake_structure)
    cv = upload(client, headers, content=make_pdf()).json()
    result = analysis(client, headers, cv["id"])
    assert result["engine"] == "claude" and result["draft"]["domain"] == "Développement mobile"
    assert result["draft"]["skills"] == [{"name": "Kotlin"}]  # Docker n'est pas dans le CV
    assert "nour.agrebi@example.com" not in sent["text"] and "AGREBI" not in sent["text"]


def test_repli_sur_les_regles_si_le_llm_echoue(client, headers, storage, monkeypatch):
    from app.modules.cv import structuring

    def broken(self, text):
        raise TimeoutError("API injoignable")

    monkeypatch.setattr(settings, "anthropic_api_key", "cle-de-test")
    monkeypatch.setattr(structuring.ClaudeStructurer, "structure", broken)
    cv = upload(client, headers, content=make_pdf()).json()
    result = analysis(client, headers, cv["id"])
    assert result["status"] == "PROCESSED" and result["engine"] == "regles"
