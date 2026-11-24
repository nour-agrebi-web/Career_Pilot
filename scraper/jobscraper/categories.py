"""Catégorie (domaine) de chaque offre : IT, IA, Développement, Mécanique…

Les deux sites donnent une rubrique, qu'on lit en priorité :
- Keejob : champ « Métiers » de la fiche Markdown (ex. « Développement informatique », ou une liste de
  métiers précis : « Ingénieur Informatique, Responsable Informatique ») ;
- Tanitjobs : champ occupationalCategory des données schema.org JobPosting (ex. « Informatique »,
  « Design, Marketing, Digital Marketing », « Industrie »).
Ces rubriques sont ramenées aux catégories de CareerPilot ci-dessous par mots-clés (origine « site »).

Si le site ne donne pas de rubrique, ou une rubrique hors de ces catégories (« Industrie », « Gestion »…),
la catégorie est déduite du titre, puis de la description si le site n'a donné aucune rubrique
(origine « deduite »).
Une offre peut avoir plusieurs catégories ; IA et Développement comptent aussi comme IT.

Noms et mots-clés alignés sur backend/app/modules/offers/search.py et android/.../domain/OfferSearch.kt.
"""
from __future__ import annotations

import re
import unicodedata

IA, IT, DEV = "IA", "IT", "Développement"
MECA, ELEC, FINANCE, MARKETING, COMMERCIAL, RH = (
    "Mécanique", "Électrique", "Finance", "Marketing", "Commercial", "RH")

# Ordre de priorité de la catégorie principale (la plus précise d'abord ; IT, catégorie large, en dernier).
ORDER = [IA, DEV, MECA, ELEC, FINANCE, MARKETING, COMMERCIAL, RH, IT]

KEYWORDS: dict[str, list[str]] = {
    IA: ["ia", "ai", "intelligence artificielle", "artificial intelligence", "machine learning", "deep learning",
         "apprentissage automatique", "apprentissage profond", "data scientist", "data science", "nlp", "llm",
         "vision par ordinateur", "computer vision", "reseaux de neurones", "neural network", "tensorflow",
         "pytorch", "scikit learn", "chatbot", "ia generative", "genai"],
    DEV: ["developpement", "developpeur", "developpeuse", "developer", "development", "dev", "programmeur",
          "programmation", "programming", "logiciel", "software", "fullstack", "full stack", "frontend",
          "front end", "backend", "back end", "mobile", "android", "ios", "web", "java", "python", "javascript",
          "typescript", "kotlin", "php", "react", "angular", "flutter", "django", "spring", "laravel", "node",
          "nodejs", "dotnet", "c#", "symfony"],
    IT: ["it", "informatique", "informaticien", "informaticienne", "devops", "cloud", "cybersecurite",
         "securite informatique", "cybersecurity", "systeme d information", "systemes d information", "si",
         "sysadmin", "administrateur systeme", "administrateur reseau", "reseau informatique", "base de donnees",
         "sql", "data", "helpdesk", "support informatique", "erp", "odoo", "sap", "linux", "docker", "kubernetes"],
    MECA: ["mecanique", "mecanicien", "mecanicienne", "genie mecanique", "electromecanique", "electromecanicien",
           "maintenance industrielle", "usinage", "soudure", "soudeur", "chaudronnerie", "chaudronnier",
           "tourneur", "fraiseur", "cao", "solidworks", "catia", "autocad", "productique", "automobile",
           "hydraulique", "pneumatique"],
    ELEC: ["electrique", "electricite", "electricien", "electronique", "electronicien", "genie electrique",
           "electrotechnique", "automatisme", "automaticien", "plc", "cablage", "electromecanique"],
    FINANCE: ["finance", "financier", "financiere", "comptabilite", "comptable", "audit", "auditeur", "fiscalite",
              "controle de gestion", "controleur de gestion", "tresorerie", "banque", "bancaire", "assurance"],
    MARKETING: ["marketing", "communication", "community manager", "seo", "sea", "content", "contenu",
                "reseaux sociaux", "social media", "brand", "marque", "publicite"],
    COMMERCIAL: ["commercial", "commerciale", "vente", "ventes", "vendeur", "vendeuse", "business developer",
                 "account manager", "chef de rayon", "televente", "teleconseiller", "prospection"],
    RH: ["rh", "ressources humaines", "recrutement", "recruteur", "talent acquisition", "paie",
         "gestion du personnel"],
}

# Rubriques des sites qui ne contiennent aucun des mots-clés ci-dessus.
SITE_KEYWORDS: dict[str, list[str]] = {
    IT: ["technologie de l information", "technologies de l information", "telecommunications"],
    COMMERCIAL: ["commerce", "commerce de detail"],
    MARKETING: ["digital marketing"],
}

# Mots trop vagues dans une description (« bonne communication », « version mobile », « data ») :
# ils ne comptent que dans le titre, les compétences ou la rubrique du site.
WEAK_IN_DESCRIPTION = {"communication", "content", "contenu", "marque", "brand", "mobile", "web", "data", "dev",
                       "vente", "ventes", "commercial", "commerciale", "audit", "assurance", "automobile", "paie"}

# Sigles qui ne comptent que s'ils sont écrits en majuscules dans un titre ou une description.
ACRONYMS = {"ia", "ai", "it", "si", "sea", "tic"}

_SEPARATORS = re.compile(r"[^\w#+]+|_+")


def _strip_accents(text: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFD", text) if unicodedata.category(c) != "Mn")


def _words(text: str | None, strict: bool) -> str:
    """« Développeur C# / IA » -> « developpeur c# ia » ; avec strict, un sigle en minuscules devient « _ »."""
    out = []
    for w in _SEPARATORS.split(_strip_accents(text or "")):
        if not w:
            continue
        low = w.lower()
        out.append("_" if strict and low in ACRONYMS and w != w.upper() else low)
    return " " + " ".join(out) + " "


_NORMALIZED = {cat: [" ".join(_words(k, False).split()) for k in kws] for cat, kws in KEYWORDS.items()}
_NORMALIZED_SITE = {cat: [" ".join(_words(k, False).split()) for k in kws] for cat, kws in SITE_KEYWORDS.items()}


def _match(texts: list[str], strict: bool, skip: set[str] = frozenset(), site: bool = False) -> set[str]:
    found = set()
    for text in texts:
        words = _words(text, strict)
        for cat in ORDER:
            keywords = _NORMALIZED[cat] + (_NORMALIZED_SITE.get(cat, []) if site else [])
            if any(f" {k} " in words for k in keywords if k not in skip):
                found.add(cat)
    return found


def split_labels(value) -> list[str]:
    """« Industrie , Textile » ou ["A", "B"] -> ["Industrie", "Textile"], sans doublons (Keejob en répète)."""
    if not value:
        return []
    parts = value if isinstance(value, list) else str(value).split(",")
    out: list[str] = []
    for p in parts:
        p = " ".join(str(p).split())
        if p and p.casefold() not in (o.casefold() for o in out):
            out.append(p)
    return out


def ordered(categories: set[str]) -> list[str]:
    if categories & {IA, DEV}:
        categories = categories | {IT}
    return [c for c in ORDER if c in categories]


def categorize(source_labels: list[str] | None, title: str | None, description: str | None = None) -> dict:
    """Renvoie {category, categories, category_origin}. category_origin : « site », « deduite » ou None."""
    site = _match(list(source_labels or []), strict=False, site=True)
    from_title = _match([title or ""], strict=True)
    if site:
        cats, origin = set(site), "site"
        # Une rubrique large (« Informatique ») ne dit pas s'il s'agit de développement ou d'IA : le titre précise.
        if IT in site:
            cats |= from_title & {IA, DEV}
    else:
        cats = from_title
        # Description seulement si le site ne donne aucune rubrique : une rubrique hors catégories
        # (« Centre d'appels ») vaut mieux qu'une déduction tirée d'un « SQL apprécié » en fin d'annonce.
        if not cats and not source_labels:
            cats = _match([description or ""], strict=True, skip=WEAK_IN_DESCRIPTION)
        origin = "deduite" if cats else None
    result = ordered(cats)
    return {"category": result[0] if result else None, "categories": result, "category_origin": origin}


def normalize_category(value: str | None) -> str | None:
    """« ia », « developpement », « Mecanique » -> nom canonique ; None si inconnu."""
    key = " ".join(_words(value, False).split())
    return next((c for c in ORDER if " ".join(_words(c, False).split()) == key), None)
