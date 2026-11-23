"""Score de pertinence offre ↔ CV, de 0 à 100, par mots-clés pondérés (première version, sans IA).

Répartition (modifiable dans le profil, clé "weights") :
  compétences 60  : somme des poids des compétences du CV trouvées dans l'offre, plafonnée à "saturation"
  intitulé    25  : un mot-clé de métier visé apparaît dans le titre (25) ou seulement dans le texte (10)
  lieu        10  : ville souhaitée ou télétravail (10), lieu inconnu (5)
  contrat      5  : type de contrat souhaité (5), inconnu (2)
Un mot exclu (ex. « commercial ») dans le titre plafonne le score à 20.
Ce calcul est volontairement isolé : il pourra être remplacé par une similarité d'embeddings sans toucher au reste.
"""
from __future__ import annotations

import hashlib
import json
import re
from functools import lru_cache

from .normalize import fold

DEFAULT_WEIGHTS = {"skills": 60, "title": 25, "location": 10, "contract": 5}


# Clés qui n'influencent pas le calcul : les changer ne doit PAS provoquer de recalcul.
_NOT_SCORING = {"threshold"}


def profile_hash(profile: dict) -> str:
    """Version du profil. Le seuil en est exclu : changer le seuil ne change pas les scores."""
    useful = {k: v for k, v in profile.items() if k not in _NOT_SCORING and not k.startswith("_")}
    return hashlib.sha256(json.dumps(useful, sort_keys=True, ensure_ascii=False).encode()).hexdigest()[:12]


@lru_cache(maxsize=4096)
def _pattern(keyword: str) -> re.Pattern:
    # Bornes « pas une lettre/chiffre » (et pas # ou + après) au lieu de \b : C#, .NET, C++ marchent et « c » ne matche pas « c#».
    # Chaque mot accepte un pluriel en -s/-x (« conseiller client » trouve « Conseillers Clients »).
    words = [re.escape(w) + ("[sx]?" if w[-1].isalpha() else "") for w in fold(keyword).split(" ")]
    return re.compile(rf"(?<![a-z0-9]){'[ -]+'.join(words)}(?![a-z0-9#+])")


def _has(keyword: str, text: str) -> bool:
    return bool(_pattern(keyword).search(text))


def score_offer(offer: dict, profile: dict) -> tuple[int, dict]:
    """`offer` est un document de la collection offers (format du backend)."""
    w = {**DEFAULT_WEIGHTS, **profile.get("weights", {})}
    title = fold(offer.get("title"))
    body = fold(" ".join(filter(None, [offer.get("title"), offer.get("description"), offer.get("company")])))

    skills: dict[str, float] = profile.get("skills", {})
    synonyms: dict[str, list[str]] = profile.get("synonyms", {})
    found, missing = [], []
    for skill, weight in skills.items():
        variants = [skill, *synonyms.get(skill, [])]
        (found if any(_has(v, body) for v in variants) else missing).append((skill, weight))
    saturation = profile.get("saturation", 6)
    found_weight = sum(wt for _, wt in found)
    s_skills = w["skills"] * min(1.0, found_weight / saturation) if saturation else 0

    titles = profile.get("titles", [])
    if any(_has(t, title) for t in titles):
        s_title = w["title"]
    elif any(_has(t, body) for t in titles):
        s_title = w["title"] * 0.4
    else:
        s_title = 0

    city = fold((offer.get("location") or {}).get("city"))
    cities = [fold(c) for c in profile.get("cities", [])]
    if offer.get("work_mode") == "teletravail" or (city and any(c in city for c in cities)):
        s_loc = w["location"]
    elif not city:
        s_loc = w["location"] * 0.5
    else:
        s_loc = 0

    contracts = profile.get("contracts", [])
    if not offer.get("type"):
        s_contract = w["contract"] * 0.4
    else:
        s_contract = w["contract"] if offer["type"] in contracts else 0

    total = s_skills + s_title + s_loc + s_contract
    excluded = [x for x in profile.get("exclude", []) if _has(x, title)]
    if excluded:
        total = min(total, 20)
    score = max(0, min(100, round(total)))
    details = {
        "competences": round(s_skills, 1), "intitule": round(s_title, 1),
        "lieu": round(s_loc, 1), "contrat": round(s_contract, 1),
        "trouvees": [s for s, _ in sorted(found, key=lambda x: -x[1])],
        "manquantes_principales": [s for s, _ in sorted(missing, key=lambda x: -x[1])[:5]],
        "exclusions": excluded,
        "partiel": not offer.get("detail_fetched"),   # True : score calculé sur la ligne de liste seulement
    }
    return score, details
