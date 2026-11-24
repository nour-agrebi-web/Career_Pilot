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
