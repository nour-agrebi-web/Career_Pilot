"""Recherche d'offres (GET /offers?q=) : mot entier, sans casse ni accents, et par domaine."""
from app.modules.offers.search import matches


def offer(title, description="", skills=(), company="Nexa", city="Tunis"):
    return {"title": title, "description": description, "required_skills": list(skills), "company": company,
            "location": {"city": city}}


LITTERAIRE = offer("Traducteur littéraire", "Traduction d'ouvrages littéraires.", ["Rédaction"])
SUPPORT = offer("Technicien support IT", "Assistance aux utilisateurs.", ["Windows"])
DATA = offer("Data Scientist junior", "Modèles de machine learning pour la prévision.", ["Python", "Pandas"])
INGENIEUR_IA = offer("Ingénieur IA", "Déploiement de modèles.", ["Python"])
ANDROID = offer("Développeur Android", "Application mobile.", ["Kotlin"])
MECA = offer("Technicien de maintenance", "Maintenance industrielle.", ["Soudure"])
ANGLAIS = offer("Sales assistant", "It is a great opportunity, j'ai dit oui.", ["Excel"])
ALL = [LITTERAIRE, SUPPORT, DATA, INGENIEUR_IA, ANDROID, MECA, ANGLAIS]


def search(q):
    return [o["title"] for o in ALL if matches(o, q)]


def test_it_ne_trouve_plus_litteraire():
    found = search("IT")
    assert LITTERAIRE["title"] not in found and ANGLAIS["title"] not in found and MECA["title"] not in found
    assert {SUPPORT["title"], ANDROID["title"], DATA["title"]} <= set(found)
    assert search("it") == found


def test_ia_trouve_le_mot_et_le_domaine():
    found = search("IA")
    assert INGENIEUR_IA["title"] in found
    assert DATA["title"] in found  # parle de machine learning sans écrire « IA »
    assert ANDROID["title"] not in found and LITTERAIRE["title"] not in found and ANGLAIS["title"] not in found
    assert search("ia") == found


def test_mecanique_et_developpement():
    assert search("mécanique") == search("mecanique") == [MECA["title"]]
    found = search("développement")
    assert ANDROID["title"] in found and MECA["title"] not in found and LITTERAIRE["title"] not in found


def test_mot_entier_et_plusieurs_mots():
    assert search("litteraire") == [LITTERAIRE["title"]]
    assert search("androi") == [ANDROID["title"]]
    assert search("tra") == []
    assert search("développeur kotlin") == [ANDROID["title"]]
    assert search("") == [o["title"] for o in ALL]


# --- Catégorie enregistrée par le scraper (champ categories) ---

def test_categorie_enregistree_prime_sur_les_mots_cles():
    # Le texte parle de Python, mais le site range l'offre en Finance : « développement » ne la trouve plus.
    analyste = {**offer("Analyste financier", "Modèles Excel et scripts Python."), "categories": ["Finance"]}
    assert not matches(analyste, "développement")
    assert matches(analyste, "finance") and matches(analyste, "Comptabilité")
    # Et une offre rangée en IA par le site est trouvée même si son texte ne le dit pas.
    vision = {**offer("Ingénieur R&D", "Projet innovant."), "categories": ["IA", "IT"]}
    assert matches(vision, "IA") and matches(vision, "informatique") and not matches(vision, "mécanique")


def test_categorie_vide_et_mot_tape_dans_le_titre():
    sans = {**offer("Conseiller mécanique auto", "Accueil clients."), "categories": []}
    assert matches(sans, "mécanique")          # le mot tapé est écrit dans l'offre
    assert not matches(sans, "IT")


def test_sans_champ_categories_repli_mots_cles():
    assert "categories" not in DATA and matches(DATA, "IA")


def test_nom_de_categorie_normalise():
    from app.modules.offers.search import category_of
    assert category_of("ia") == "IA" and category_of("Informatique") == "IT"
    assert category_of("developpement") == category_of("Développement") == "Développement"
    assert category_of("MECANIQUE") == "Mécanique" and category_of("cuisine") is None


def _insert(db, title, categories=None, description=""):
    from datetime import datetime, timezone
    doc = {"source": "Keejob", "source_id": title, "title": title, "company": "Nexa", "type": "emploi",
           "location": {"city": "Tunis", "country": "Tunisie"}, "description": description,
           "required_skills": [], "status": "ACTIVE", "publication_date": datetime.now(timezone.utc)}
    if categories is not None:
        doc.update(categories=categories, category=categories[0] if categories else None)
    db.offers.insert_one(doc)


def test_api_filtre_category(client, db, headers):
    _insert(db, "Développeur Kotlin", ["Développement", "IT"])
    _insert(db, "Ingénieur ML", ["IA", "IT"])
    _insert(db, "Technicien de maintenance", ["Mécanique"])
    _insert(db, "Conseiller clientèle", [])
    _insert(db, "Data Scientist (ancienne offre)", description="Machine learning et Python.")  # pas de champ

    def titles(category):
        response = client.get("/offers", params={"category": category}, headers=headers)
        assert response.status_code == 200
        return sorted(o["title"] for o in response.json())

    assert titles("IA") == ["Data Scientist (ancienne offre)", "Ingénieur ML"]
    assert titles("ia") == titles("IA")
    assert titles("developpement") == ["Data Scientist (ancienne offre)", "Développeur Kotlin"]  # repli : Python
    assert titles("IT") == ["Data Scientist (ancienne offre)", "Développeur Kotlin", "Ingénieur ML"]
    assert titles("Mécanique") == ["Technicien de maintenance"]
    first = client.get("/offers", params={"category": "IA"}, headers=headers).json()
    assert {o["title"]: o["category"] for o in first}["Ingénieur ML"] == "IA"
    assert len(client.get("/offers", headers=headers).json()) == 5
    response = client.get("/offers", params={"category": "cuisine"}, headers=headers)
    assert response.status_code == 422 and "Catégorie inconnue" in response.json()["detail"]
