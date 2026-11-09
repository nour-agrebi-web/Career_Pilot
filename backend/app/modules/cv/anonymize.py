"""Retrait des coordonnées personnelles avant tout envoi à un modèle d'IA (US-2.2.1).

Règle de confidentialité : le nom, l'e-mail, le téléphone et l'adresse sont retirés du texte.
Les autres informations (formations, expériences, compétences) restent, car l'IA en a besoin.
"""
import re
import unicodedata

EMAIL = re.compile(r"[\w.+-]+@[\w-]+(\.[\w-]+)+")
URL = re.compile(r"(https?://\S+|www\.\S+|\b(linkedin|github|gitlab)\.com/\S*)", re.IGNORECASE)
# Numéros de téléphone : au moins 8 chiffres, avec indicatif, espaces, points ou tirets éventuels.
PHONE = re.compile(r"(?<![\w/])(\+|00)?\d[\d\s.\-()]{6,}\d(?![\w/])")
ADDRESS_WORDS = re.compile(
    r"\b(rue|avenue|av\.|bd|boulevard|impasse|cit[ée]|route|r[ée]sidence|immeuble|appartement|app\.|"
    r"code postal|b\.?p\.?|street|road)\b",
    re.IGNORECASE,
)
ADDRESS_LABEL = re.compile(r"^\s*(adresse|address|domicile)\s*:", re.IGNORECASE)

NAME_PLACEHOLDER = "[NOM]"


def _plain(text: str) -> str:
    return unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode().lower()


def _digit_count(text: str) -> int:
    return sum(c.isdigit() for c in text)


def _is_not_phone(value: str) -> bool:
    """Évite de prendre une période (« 2023 - 2024 ») pour un numéro de téléphone."""
    if _digit_count(value) < 8:
        return True
    groups = re.findall(r"\d+", value)
    return any(re.fullmatch(r"(19|20)\d{2}", g) for g in groups) and all(len(g) <= 4 for g in groups)


def _looks_like_name(line: str) -> bool:
    """Une ligne de 2 à 4 mots alphabétiques commençant par une majuscule, sans chiffre (ex. « Nour AGREBI »)."""
    words = line.strip().split()
    if not 2 <= len(words) <= 4 or any(c.isdigit() for c in line):
        return False
    if any(w.lower().strip(":") in _SECTION_WORDS for w in words):
        return False
    return all(re.fullmatch(r"[^\W\d_][^\W\d_'\-.]*", w) and w[0].isupper() for w in words)


_SECTION_WORDS = {
    "curriculum", "vitae", "cv", "compétences", "competences", "expérience", "experience", "expériences",
    "formation", "formations", "langues", "profil", "skills", "education", "languages", "contact",
    "développeur", "developpeur", "ingénieur", "ingenieur", "étudiant", "etudiant", "stagiaire", "developer",
    "engineer", "student", "licence", "master",
}


def anonymize(text: str, extra_values: list[str] | None = None) -> str:
    lines = text.splitlines()
    result = []
    name_parts: list[str] = []
    for index, line in enumerate(lines):
        if ADDRESS_LABEL.match(line) or (ADDRESS_WORDS.search(line) and _digit_count(line) >= 1 and len(line) < 120):
            result.append("[ADRESSE]")
            continue
        # Le nom figure presque toujours dans les premières lignes du CV.
        if index < 5 and not name_parts and _looks_like_name(line):
            name_parts = [w for w in line.split() if len(w) > 2]
            result.append(NAME_PLACEHOLDER)
            continue
        result.append(line)

    cleaned = "\n".join(result)
    cleaned = EMAIL.sub("[EMAIL]", cleaned)
    cleaned = URL.sub("[LIEN]", cleaned)
    cleaned = PHONE.sub(lambda m: m.group() if _is_not_phone(m.group()) else "[TÉLÉPHONE]", cleaned)
    # Le nom peut réapparaître plus loin (signature, « Nour Agrebi – Développeuse ») : on le retire aussi.
    for value in name_parts + [v for v in (extra_values or []) if v]:
        cleaned = re.sub(rf"\b{re.escape(value)}\b", NAME_PLACEHOLDER, cleaned, flags=re.IGNORECASE)
    return cleaned
