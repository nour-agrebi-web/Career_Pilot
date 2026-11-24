"""Recherche d'offres par mot entier et par domaine (paramètre ``q`` de GET /offers).

Mêmes règles que l'app Android (android/.../domain/OfferSearch.kt), à garder alignées :
- mot entier, sans casse ni accents (« IT » ne trouve plus « littéraire ») ; un mot de 4 lettres ou plus
  trouve aussi les mots qui commencent par lui (« pyth » → « Python ») ;
- « IA », « IT », « mécanique », « développement »… trouvent les offres de cette catégorie. Le scraper
  enregistre la catégorie de chaque offre (champ ``categories``, voir scraper/jobscraper/categories.py) :
  elle est utilisée telle quelle. Pour une offre sans ce champ (offres d'exemple, offres pas encore
  rattrapées), la catégorie est déduite de mots-clés, comme avant ;
- les sigles courts (IA, IT, AI…) ne comptent dans un titre ou une description que s'ils y sont en
  majuscules (« it » anglais, « j'ai »).
"""
import re
import unicodedata

AI_KEYWORDS = [
    "ia", "ai", "intelligence artificielle", "artificial intelligence", "machine learning", "deep learning",
    "apprentissage automatique", "apprentissage profond", "data scientist", "data science", "nlp", "llm",
    "vision par ordinateur", "computer vision", "reseaux de neurones", "neural network", "tensorflow",
    "pytorch", "scikit learn", "chatbot", "ia generative", "genai",
]

DEV_KEYWORDS = [
    "developpement", "developpeur", "developpeuse", "developer", "development", "dev", "programmeur",
    "programmation", "programming", "logiciel", "software", "fullstack", "full stack", "frontend",
    "front end", "backend", "back end", "mobile", "android", "ios", "web", "java", "python", "javascript",
    "typescript", "kotlin", "php", "react", "angular", "flutter", "django", "spring", "laravel", "node",
    "nodejs", "dotnet", "c#", "symfony",
]

IT_KEYWORDS = [
    "it", "informatique", "informaticien", "informaticienne", "devops", "cloud", "cybersecurite",
    "securite informatique", "cybersecurity", "systeme d information", "systemes d information", "si",
    "sysadmin", "administrateur systeme", "administrateur reseau", "reseau informatique", "base de donnees",
    "sql", "data", "helpdesk", "support informatique", "erp", "odoo", "sap", "linux", "docker", "kubernetes",
] + DEV_KEYWORDS + AI_KEYWORDS

# (nom de la catégorie, alias tapés par l'utilisateur, mots-clés recherchés dans l'offre)
DOMAINS: list[tuple[str, set[str], list[str]]] = [
    ("IA", {"ia", "ai", "intelligence artificielle", "artificial intelligence", "machine learning"}, AI_KEYWORDS),
    ("IT", {"it", "informatique", "info", "tic", "technologies de l information"}, IT_KEYWORDS),
    ("Développement", {"developpement", "developpeur", "developpeuse", "developper", "dev", "programmation", "developer",
      "development"}, DEV_KEYWORDS),
    ("Mécanique", {"mecanique", "meca", "mecanicien", "mecanicienne", "genie mecanique"}, [
        "mecanique", "mecanicien", "mecanicienne", "genie mecanique", "electromecanique", "electromecanicien",
        "maintenance industrielle", "usinage", "soudure", "soudeur", "chaudronnerie", "chaudronnier",
        "tourneur", "fraiseur", "cao", "solidworks", "catia", "autocad", "productique", "automobile",
        "hydraulique", "pneumatique"]),
    ("Électrique", {"electrique", "electricite", "electronique", "electricien", "genie electrique"}, [
        "electrique", "electricite", "electricien", "electronique", "electronicien", "genie electrique",
        "electrotechnique", "automatisme", "automaticien", "plc", "cablage", "electromecanique"]),
    ("Finance", {"finance", "comptabilite", "comptable", "audit", "banque"}, [
        "finance", "financier", "financiere", "comptabilite", "comptable", "audit", "auditeur", "fiscalite",
        "controle de gestion", "controleur de gestion", "tresorerie", "banque", "bancaire", "assurance"]),
    ("Marketing", {"marketing", "communication", "digital marketing", "marketing digital"}, [
        "marketing", "communication", "community manager", "seo", "sea", "content", "contenu",
        "reseaux sociaux", "social media", "brand", "marque", "publicite"]),
    ("Commercial", {"commercial", "vente", "ventes", "commerciale"}, [
        "commercial", "commerciale", "vente", "ventes", "vendeur", "vendeuse", "business developer",
        "account manager", "chef de rayon", "televente", "teleconseiller", "prospection"]),
    ("RH", {"rh", "ressources humaines", "recrutement"}, [
        "rh", "ressources humaines", "recrutement", "recruteur", "talent acquisition", "paie",
        "gestion du personnel"]),
]

# Sigles qui ne comptent que s'ils sont écrits en majuscules dans un titre ou une description.
ACRONYMS = {"ia", "ai", "it", "si", "sea", "tic"}

_SEPARATORS = re.compile(r"[^\w#+]+|_+")


def _strip_accents(text: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFD", text) if unicodedata.category(c) != "Mn")


def _split(text: str) -> list[str]:
    return [w for w in _SEPARATORS.split(_strip_accents(text or "")) if w]


def tokens(text: str) -> list[str]:
    """Mots en minuscules, sans accents ; la ponctuation sépare les mots (« C# » et « C++ » sont gardés)."""
    return [w.lower() for w in _split(text)]


def _strict_tokens(text: str) -> list[str]:
    """Comme tokens(), mais un sigle écrit en minuscules (« it », « j'ai ») est remplacé par un blanc."""
    return ["_" if w.lower() in ACRONYMS and w != w.upper() else w.lower() for w in _split(text)]


def category_of(text: str) -> str | None:
    """« ia », « Informatique », « développeur »… -> nom de la catégorie (« IA », « IT »…) ; None sinon."""
    normalized = " ".join(tokens(text))
    return next((name for name, aliases, _ in DOMAINS if normalized in aliases or normalized == _norm(name)), None)


def _norm(text: str) -> str:
    return " ".join(tokens(text))


# Mots-clés normalisés une seule fois.
_KEYWORDS = {name: [_norm(k) for k in keywords] for name, _, keywords in DOMAINS}


def in_category(offer: dict, category: str, text: "_OfferText | None" = None) -> bool:
    """Catégorie enregistrée par le scraper si l'offre en a une ; sinon déduite des mots-clés (repli)."""
    if offer.get("categories") is not None:
        return category in offer["categories"]
    return (text or _OfferText(offer)).has_any(_KEYWORDS[category])


class _OfferText:
    def __init__(self, offer: dict):
        location = offer.get("location") or {}
        city = location.get("city", "") if isinstance(location, dict) else str(location)
        prose = [offer.get("title", ""), offer.get("description", ""), offer.get("company", ""), city or ""]
        skills = offer.get("required_skills") or []
        self.words = [w for text in prose + skills for w in tokens(text)]
        self.prose_strict = " " + " | ".join(" ".join(_strict_tokens(t)) for t in prose) + " "
        self.skills = " " + " | ".join(" ".join(tokens(s)) for s in skills) + " "

    def _contains(self, phrase: str) -> bool:
        return bool(phrase) and (f" {phrase} " in self.prose_strict or f" {phrase} " in self.skills)

    def has_word(self, word: str) -> bool:
        if word in ACRONYMS:
            return self._contains(word)
        return any(w == word or (len(word) >= 4 and w.startswith(word)) for w in self.words)

    def has_any(self, keywords: list[str]) -> bool:
        return any(self._contains(k) for k in keywords)


def matches(offer: dict, query: str) -> bool:
    """Vrai si l'offre (format offer_out) correspond à la recherche (vide = toutes les offres)."""
    normalized = " ".join(tokens(query))
    if not normalized:
        return True
    text = _OfferText(offer)
    category = category_of(normalized)
    if category and in_category(offer, category, text):
        return True
    return all(text.has_word(w) or ((c := category_of(w)) is not None and in_category(offer, c, text))
               for w in normalized.split(" "))
