"""Score de compatibilité entre un profil validé et une offre (règles métier du cahier des charges).

Score sur 100 = 40 similarité + 30 compétences clés + 15 niveau et expérience + 15 préférences.

La similarité sémantique est ici une version provisoire, sans IA : recouvrement des mots-clés
du profil et de l'offre. Elle sera remplacée par les embeddings multilingues (Epic 4) sans
changer le reste du calcul.
"""
import math
import re
import unicodedata
from dataclasses import dataclass, field

WEIGHTS = {"semantic": 40, "skills": 30, "level": 15, "preferences": 15}
LEVELS = ["stage", "junior", "confirme"]

# Synonymes normalisés (même compétence écrite autrement).
SYNONYMS = {
    "js": "javascript", "ts": "typescript", "reactjs": "react", "react.js": "react",
    "node": "nodejs", "node.js": "nodejs", "postgres": "postgresql", "mongo": "mongodb",
    "k8s": "kubernetes", "py": "python", "ml": "machine learning", "ia": "intelligence artificielle",
}

STOPWORDS = {
    "les", "des", "une", "pour", "avec", "dans", "sur", "par", "est", "aux", "vous", "nous", "and", "the",
    "for", "with", "you", "our", "votre", "notre", "qui", "que", "son", "ses", "leur", "plus", "être",
    "etre", "afin", "chez", "entre", "ainsi", "tout", "tous", "très", "tres", "bonne", "bon",
}


def normalize(text: str) -> str:
    text = unicodedata.normalize("NFKD", text.strip().lower())
    text = "".join(c for c in text if not unicodedata.combining(c))
    return SYNONYMS.get(text, text)


def keywords(text: str) -> set[str]:
    words = re.findall(r"[a-z0-9+#.]{3,}", normalize(text))
    return {SYNONYMS.get(w.strip("."), w.strip(".")) for w in words} - STOPWORDS


@dataclass
class MatchResult:
    score: int
    breakdown: dict[str, int]
    reasons: list[str] = field(default_factory=list)
    missing_skills: list[str] = field(default_factory=list)


def eliminated(profile: dict, prefs: dict, offer: dict) -> str | None:
    """Filtres éliminatoires ; renvoie la raison, ou None si l'offre reste candidate."""
    if offer.get("status", "ACTIVE") != "ACTIVE":
        return "offre expirée"
    if offer["type"] not in prefs["job_types"]:
        return "type d'offre non souhaité"
    if offer.get("work_mode") != "teletravail" and prefs.get("locations"):
        cities = {normalize(loc["city"]) for loc in prefs["locations"]}
        if normalize(offer.get("location", {}).get("city", "")) not in cities:
            return "zone hors périmètre"
    if offer.get("work_mode") == "teletravail" and not prefs.get("remote_work", True) and prefs.get("locations"):
        return "télétravail non souhaité"
    language = offer.get("language")
    if language and normalize(language) not in {normalize(lang["name"]) for lang in profile.get("languages", [])}:
        return "langue exigée non maîtrisée"
    return None


def score(profile: dict, prefs: dict, offer: dict) -> MatchResult | None:
    if eliminated(profile, prefs, offer):
        return None
    reasons: list[str] = []

    # 1. Compétences clés (30) : part des compétences exigées présentes dans le profil.
    profile_skills = {normalize(s["name"]): s["name"] for s in profile.get("skills", [])}
    required = offer.get("required_skills", [])
    matched = [s for s in required if normalize(s) in profile_skills]
    missing = [s for s in required if normalize(s) not in profile_skills]
    skills_ratio = len(matched) / len(required) if required else 0.5
    if matched:
        reasons.append(f"{', '.join(matched[:3])} présent{'s' if len(matched) > 1 else ''} dans votre profil")

    # 2. Similarité (40) : cosinus entre les ensembles de mots-clés.
    profile_words = set()
    for s in profile.get("skills", []):
        profile_words |= keywords(s["name"])
    for e in profile.get("experiences", []):
        profile_words |= keywords(e["position"])
    for e in profile.get("educations", []):
        profile_words |= keywords(e["degree"])
    profile_words |= keywords(profile.get("domain") or "")
    offer_words = keywords(f"{offer['title']} {offer.get('description', '')} {' '.join(required)}")
    common = profile_words & offer_words
    cosine = len(common) / math.sqrt(len(profile_words) * len(offer_words)) if profile_words and offer_words else 0
    semantic_ratio = min(1.0, cosine * 2.5)

    # 3. Niveau et expérience (15).
    wanted, have = offer.get("level"), profile.get("level")
    if not wanted or not have:
        level_ratio = 0.5
    else:
        gap = LEVELS.index(have) - LEVELS.index(wanted)
        level_ratio = 1.0 if gap == 0 else 0.7 if gap > 0 else 0.3 if gap == -1 else 0.0
        if gap == 0:
            reasons.append(f"Niveau demandé ({wanted}) conforme à votre niveau")

    # 4. Préférences (15) : type (déjà filtré), zone ou télétravail, langue.
    pref_points = 1.0  # le type est forcément souhaité après les filtres
    city = offer.get("location", {}).get("city")
    if offer.get("work_mode") == "teletravail":
        pref_points += 1.0
        reasons.append("Télétravail possible")
    elif not prefs.get("locations") or city:
        pref_points += 1.0
        if prefs.get("locations") and city:
            reasons.append(f"{city} fait partie de vos localisations")
    pref_points += 1.0  # langue : vérifiée par les filtres
    pref_ratio = pref_points / 3

    breakdown = {
        "semantic": round(WEIGHTS["semantic"] * semantic_ratio),
        "skills": round(WEIGHTS["skills"] * skills_ratio),
        "level": round(WEIGHTS["level"] * level_ratio),
        "preferences": round(WEIGHTS["preferences"] * pref_ratio),
    }
    return MatchResult(sum(breakdown.values()), breakdown, reasons, missing)
