"""Structuration du texte du CV en profil (US-2.2.1).

Deux moteurs, avec la même sortie (un brouillon de profil) :
- `ClaudeStructurer` : LLM via l'API Claude, sortie JSON structurée. Utilisé si ANTHROPIC_API_KEY est défini.
- `RuleStructurer` : analyse locale par règles (titres de sections, dates). Sans clé, en test, ou si l'API échoue.

Dans les deux cas, `keep_grounded` retire tout élément absent du texte du CV : l'agent n'ajoute jamais
une expérience, un diplôme ou une compétence qui n'y figure pas (règle métier).

Format du brouillon (mêmes champs que le profil) :
{ domain, level, skills: [{name}], experiences: [{position, company, start_date, end_date}],
  educations: [{degree, institution, start_date, end_date}], languages: [{name, level}] }
Les dates sont au format AAAA-MM-JJ (le 1er du mois si seul le mois est connu), ou null.
"""
import json
import logging
import re
import unicodedata
from datetime import date

logger = logging.getLogger(__name__)

LEVELS = ("stage", "junior", "confirme")
LANGUAGE_LEVELS = ("A1", "A2", "B1", "B2", "C1", "C2", "natif")


def plain(text: str) -> str:
    """Minuscules sans accents, pour comparer des textes."""
    return unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode().lower()


def empty_draft() -> dict:
    return {"domain": None, "level": None, "skills": [], "experiences": [], "educations": [], "languages": []}


# --------------------------------------------------------------------------------------------
# Moteur local par règles
# --------------------------------------------------------------------------------------------

SECTION_TITLES = {
    "skills": ("competences", "competence", "skills", "technologies", "outils", "savoir-faire",
               "competences techniques", "hard skills", "technical skills", "logiciels", "langages"),
    "experiences": ("experiences", "experience", "experiences professionnelles", "experience professionnelle",
                    "parcours professionnel", "stages", "stage", "work experience", "professional experience",
                    "emplois"),
    "educations": ("formation", "formations", "education", "diplomes", "cursus", "etudes", "parcours academique",
                   "formation academique", "academic background"),
    "languages": ("langues", "langue", "languages", "langues parlees"),
    # Sections connues mais ignorées : elles terminent la section en cours.
    "other": ("projets", "projects", "centres d'interet", "interets", "loisirs", "hobbies", "certifications",
              "profil", "a propos", "about", "contact", "coordonnees", "references", "activites associatives",
              "vie associative", "soft skills", "qualites", "objectif"),
}

MONTHS = {
    "janvier": 1, "janv": 1, "jan": 1, "january": 1, "fevrier": 2, "fevr": 2, "fev": 2, "feb": 2, "february": 2,
    "mars": 3, "mar": 3, "march": 3, "avril": 4, "avr": 4, "apr": 4, "april": 4, "mai": 5, "may": 5,
    "juin": 6, "jun": 6, "june": 6, "juillet": 7, "juil": 7, "jul": 7, "july": 7, "aout": 8, "aug": 8,
    "august": 8, "septembre": 9, "sept": 9, "sep": 9, "september": 9, "octobre": 10, "oct": 10, "october": 10,
    "novembre": 11, "nov": 11, "november": 11, "decembre": 12, "dec": 12, "december": 12,
}
_MONTH_NAMES = "|".join(sorted(MONTHS, key=len, reverse=True))
_DATE = rf"(?:(?:\d{{1,2}}[/.-])?(?:19|20)\d{{2}}|(?:{_MONTH_NAMES})\.?\s+(?:19|20)\d{{2}})"
_NOW = r"(?:present|aujourd'hui|aujourd hui|en cours|actuel|actuellement|now|current|ce jour)"
DATE_RANGE = re.compile(rf"({_DATE})\s*(?:-|–|—|a|au|to|until|jusqu'a)\s*({_DATE}|{_NOW})", re.IGNORECASE)
SINGLE_DATE = re.compile(rf"(?:depuis|since)?\s*({_DATE})", re.IGNORECASE)

BULLETS = re.compile(r"^[\s•●▪◦\-–*·>]+")
SKILL_SPLIT = re.compile(r"[,;•●▪|/·]|\s-\s|\s–\s")
LANG_LEVEL_WORDS = [
    (re.compile(r"\b(A1|A2|B1|B2|C1|C2)\b", re.IGNORECASE), None),
    (re.compile(r"natif|native|maternelle|mother tongue", re.IGNORECASE), "natif"),
    (re.compile(r"bilingue|bilingual", re.IGNORECASE), "C2"),
    (re.compile(r"courant|fluent|avance|advanced", re.IGNORECASE), "C1"),
    (re.compile(r"intermediaire|intermediate|bon niveau|professionnel", re.IGNORECASE), "B1"),
    (re.compile(r"notions|debutant|beginner|elementaire|scolaire|basic", re.IGNORECASE), "A2"),
]
DEGREE_WORDS = re.compile(
    r"\b(licence|license|master|mastere|bachelor|baccalaureat|bac|bts|dut|doctorat|phd|ingenieur|diplome|"
    r"cycle|prepa|preparatoire|certificat|degree)\b", re.IGNORECASE)
INSTITUTION_WORDS = re.compile(
    r"\b(iset|universite|university|ecole|school|institut|institute|faculte|lycee|college|esprit|insat|enit|"
    r"ensi|isg|ihec|fst|isi|polytech|academy|academie)\b", re.IGNORECASE)


def _section_of(line: str) -> str | None:
    title = plain(line).strip().strip(":").strip()
    title = re.sub(r"\s+", " ", re.sub(r"[^a-z' -]", " ", title)).strip()
    if not title or len(title) > 40:
        return None
    for section, titles in SECTION_TITLES.items():
        if title in titles:
            return section
    return None


def _parse_date(value: str, end: bool = False) -> str | None:
    value = plain(value).strip().rstrip(".")
    if re.fullmatch(_NOW, value):
        return None
    m = re.fullmatch(r"(\d{1,2})[/.-]((?:19|20)\d{2})", value)
    if m and 1 <= int(m.group(1)) <= 12:
        return date(int(m.group(2)), int(m.group(1)), 1).isoformat()
    m = re.fullmatch(rf"({_MONTH_NAMES})\.?\s+((?:19|20)\d{{2}})", value)
    if m:
        return date(int(m.group(2)), MONTHS[m.group(1)], 1).isoformat()
    m = re.fullmatch(r"((?:19|20)\d{2})", value)
    if m:
        # Seule l'année est connue : début de l'année, ou fin de l'année pour une date de fin.
        return date(int(m.group(1)), 12 if end else 1, 1).isoformat()
    return None


def _find_period(line: str) -> tuple[str | None, str | None, bool, tuple[int, int] | None]:
    """Renvoie (début, fin, trouvé, position) ; fin None = en cours."""
    text = plain(line)
    m = DATE_RANGE.search(text)
    if m:
        return _parse_date(m.group(1)), _parse_date(m.group(2), end=True), True, m.span()
    m = SINGLE_DATE.search(text)
    if m:
        start = _parse_date(m.group(1))
        return start, (None if "depuis" in m.group(0) or "since" in m.group(0) else start), True, m.span()
    return None, None, False, None


def _split_title(text: str) -> tuple[str, str]:
    """« Développeur mobile – Nexa, Tunis » -> (« Développeur mobile », « Nexa »)."""
    text = BULLETS.sub("", text).strip(" ,:|-–—()")
    for separator in (" chez ", " at ", " – ", " — ", " - ", " | ", " @ ", ", "):
        if separator in text:
            left, right = text.split(separator, 1)
            return left.strip(" ,:|-–—()"), right.split(",")[0].strip(" ,:|-–—()")
    return text, ""


def _entries(lines: list[str]) -> list[dict]:
    """Regroupe les lignes d'une section en entrées : une nouvelle entrée commence à chaque période."""
    entries: list[dict] = []
    for line in lines:
        start, end, found, span = _find_period(line)
        rest = line
        if found and span:
            plain_line = plain(line)
            # On retire la période de la ligne (positions identiques : plain() garde la longueur pour le français courant).
            rest = (line[:span[0]] + line[span[1]:]) if len(plain_line) == len(line) else DATE_RANGE.sub("", line)
        rest = rest.strip(" ,:|-–—()\t")
        if found:
            entries.append({"start_date": start, "end_date": end, "lines": [rest] if rest else []})
        elif entries:
            if rest and len(entries[-1]["lines"]) < 3:
                entries[-1]["lines"].append(rest)
        elif rest:
            entries.append({"start_date": None, "end_date": None, "lines": [rest]})
    return entries


class RuleStructurer:
    name = "regles"

    def structure(self, text: str) -> dict:
        sections: dict[str, list[str]] = {"skills": [], "experiences": [], "educations": [], "languages": []}
        current = None
        for raw in text.splitlines():
            line = raw.strip()
            if not line:
                continue
            section = _section_of(line)
            if section is None and ":" in line:
                # « Compétences : Kotlin, Python » sur une seule ligne
                head, tail = line.split(":", 1)
                head_section = _section_of(head)
                if head_section and head_section != "other" and tail.strip():
                    sections[head_section].append(tail.strip())
                    current = head_section
                    continue
            if section is not None:
                current = None if section == "other" else section
                continue
            if current:
                sections[current].append(line)

        draft = empty_draft()
        draft["skills"] = self._skills(sections["skills"])
        draft["languages"] = self._languages(sections["languages"])
        draft["experiences"] = self._experiences(sections["experiences"])
        draft["educations"] = self._educations(sections["educations"])
        draft["level"] = self._level(draft["experiences"])
        return draft

    @staticmethod
    def _skills(lines: list[str]) -> list[dict]:
        skills, seen = [], set()
        for line in lines:
            if ":" in line:  # « Langages : Kotlin, Java »
                line = line.split(":", 1)[1]
            for part in SKILL_SPLIT.split(BULLETS.sub("", line)):
                name = part.strip(" .()\t")
                if 1 <= len(name) <= 40 and len(name.split()) <= 4 and plain(name) not in seen:
                    seen.add(plain(name))
                    skills.append({"name": name})
        return skills[:40]

    @staticmethod
    def _languages(lines: list[str]) -> list[dict]:
        languages, seen = [], set()
        for line in lines:
            for part in re.split(r"[,;•|]", BULLETS.sub("", line)):
                part = part.strip()
                m = re.match(r"([^\W\d_]+)", part)
                if not m or len(m.group(1)) < 3:
                    continue
                name = m.group(1).capitalize()
                level = None
                for pattern, value in LANG_LEVEL_WORDS:
                    found = pattern.search(plain(part) if value else part)
                    if found:
                        level = value or found.group(1).upper()
                        break
                if plain(name) not in seen:
                    seen.add(plain(name))
                    languages.append({"name": name, "level": level})
        return languages

    @staticmethod
    def _experiences(lines: list[str]) -> list[dict]:
        result = []
        for entry in _entries(lines):
            if not entry["lines"]:
                continue
            position, company = _split_title(entry["lines"][0])
            if not company and len(entry["lines"]) > 1:
                company = _split_title(entry["lines"][1])[0]
            result.append({"position": position[:100], "company": company[:100] or None,
                           "start_date": entry["start_date"], "end_date": entry["end_date"]})
        return result

    @staticmethod
    def _educations(lines: list[str]) -> list[dict]:
        result = []
        for entry in _entries(lines):
            if not entry["lines"]:
                continue
            degree, institution = _split_title(entry["lines"][0])
            if not institution:
                for other in entry["lines"][1:]:
                    if INSTITUTION_WORDS.search(plain(other)):
                        institution = _split_title(other)[0]
                        break
            if INSTITUTION_WORDS.search(plain(degree)) and institution and DEGREE_WORDS.search(plain(institution)):
                degree, institution = institution, degree
            result.append({"degree": degree[:120], "institution": institution[:120] or None,
                           "start_date": entry["start_date"], "end_date": entry["end_date"]})
        return result

    @staticmethod
    def _level(experiences: list[dict]) -> str | None:
        if not experiences:
            return None
        if all("stag" in plain(e["position"]) or "intern" in plain(e["position"]) for e in experiences):
            return "stage"
        return None  # le niveau junior/confirmé est laissé au candidat plutôt que deviné


# --------------------------------------------------------------------------------------------
# Moteur LLM (Claude)
# --------------------------------------------------------------------------------------------

_PERIOD_PROPS = {"start_date": {"type": ["string", "null"]}, "end_date": {"type": ["string", "null"]}}

DRAFT_SCHEMA = {
    "type": "object",
    "properties": {
        "domain": {"type": ["string", "null"]},
        "level": {"type": ["string", "null"]},  # stage | junior | confirme, vérifié par clean_draft
        "skills": {"type": "array", "items": {
            "type": "object", "properties": {"name": {"type": "string"}},
            "required": ["name"], "additionalProperties": False}},
        "experiences": {"type": "array", "items": {
            "type": "object",
            "properties": {"position": {"type": "string"}, "company": {"type": ["string", "null"]}, **_PERIOD_PROPS},
            "required": ["position", "company", "start_date", "end_date"], "additionalProperties": False}},
        "educations": {"type": "array", "items": {
            "type": "object",
            "properties": {"degree": {"type": "string"}, "institution": {"type": ["string", "null"]}, **_PERIOD_PROPS},
            "required": ["degree", "institution", "start_date", "end_date"], "additionalProperties": False}},
        "languages": {"type": "array", "items": {
            "type": "object",
            "properties": {"name": {"type": "string"}, "level": {"type": ["string", "null"]}},
            "required": ["name", "level"], "additionalProperties": False}},
    },
    "required": ["domain", "level", "skills", "experiences", "educations", "languages"],
    "additionalProperties": False,
}

SYSTEM_PROMPT = """Tu es l'agent d'analyse de CV de CareerPilot, une application qui recommande des stages et des \
emplois à des étudiants et jeunes diplômés (Tunisie et Europe).

On te donne le texte d'un CV, déjà anonymisé ([NOM], [EMAIL], [TÉLÉPHONE], [ADRESSE] remplacent les coordonnées). \
Transforme-le en profil structuré au format JSON demandé.

Règles :
- N'utilise que ce qui est écrit dans le CV. N'ajoute jamais une expérience, un diplôme, une certification, une \
compétence ou une langue qui n'y figure pas, même si elle semble probable. Le candidat validera le profil, et une \
information inventée fausserait les recommandations d'offres.
- Si une information manque, mets null (ou une liste vide). Ne devine pas une date ou un niveau.
- Garde les intitulés dans la langue du CV, sans les traduire. Une compétence par élément (ex. « Kotlin », \
« Jetpack Compose »), sans phrase.
- Dates au format AAAA-MM-JJ : le 1er du mois si seul le mois est connu ; si seule l'année est connue, AAAA-01-01 \
pour un début et AAAA-12-01 pour une fin. end_date est null pour un poste ou une formation en cours.
- domain : le domaine professionnel visé en quelques mots (ex. « Développement mobile »), ou null s'il n'est pas clair.
- level : « stage » si le candidat cherche un stage ou n'a que des stages, « junior » pour moins de 3 ans \
d'expérience hors stage, « confirme » au-delà ; null si le CV ne permet pas de le dire.
- languages.level : A1 à C2 ou « natif », seulement si le CV l'indique (« courant » = C1, « bilingue » = C2, \
« langue maternelle » = natif) ; sinon null."""


class ClaudeStructurer:
    name = "claude"

    def __init__(self, api_key: str, model: str, timeout: float):
        import anthropic

        self._anthropic = anthropic
        self.model = model
        self.client = anthropic.Anthropic(api_key=api_key, timeout=timeout, max_retries=1)

    def structure(self, text: str) -> dict:
        response = self.client.beta.messages.create(
            model=self.model,
            max_tokens=8000,
            system=SYSTEM_PROMPT,
            messages=[{"role": "user", "content": f"Texte du CV :\n\n<cv>\n{text}\n</cv>"}],
            # Effort bas : une extraction simple doit tenir dans la limite de 30 s du cahier des charges.
            output_config={"effort": "low", "format": {"type": "json_schema", "schema": DRAFT_SCHEMA}},
            # Si le modèle refuse la requête, l'API la relance sur un modèle de repli.
            betas=["server-side-fallback-2026-07-01"],
            fallbacks="default",
        )
        if response.stop_reason == "refusal":
            raise RuntimeError("Le modèle a refusé d'analyser ce CV.")
        if response.stop_reason == "max_tokens":
            raise RuntimeError("Réponse du modèle tronquée.")
        raw = next(block.text for block in response.content if block.type == "text")
        return {**empty_draft(), **json.loads(raw)}


def get_structurer(settings) -> "RuleStructurer | ClaudeStructurer":
    if settings.anthropic_api_key:
        return ClaudeStructurer(settings.anthropic_api_key, settings.ai_model, settings.ai_timeout_seconds)
    return RuleStructurer()


def structure_text(text: str, settings) -> tuple[dict, str]:
    """Structure le texte (déjà anonymisé) ; repli sur les règles locales si l'API ne répond pas."""
    structurer = get_structurer(settings)
    try:
        draft = structurer.structure(text)
        engine = structurer.name
    except Exception as e:  # erreur réseau, quota de l'API, réponse invalide…
        if isinstance(structurer, RuleStructurer):
            raise
        logger.warning("Analyse par le LLM impossible (%s) : repli sur l'analyse par règles.", e)
        draft, engine = RuleStructurer().structure(text), RuleStructurer.name
    return clean_draft(keep_grounded(draft, text)), engine


# --------------------------------------------------------------------------------------------
# Garde-fous communs aux deux moteurs
# --------------------------------------------------------------------------------------------

def _appears(value: str | None, haystack: str) -> bool:
    if not value:
        return False
    needle = re.sub(r"\s+", " ", plain(value)).strip()
    return bool(needle) and needle in haystack


def keep_grounded(draft: dict, text: str) -> dict:
    """Retire tout élément qui n'apparaît pas dans le texte du CV (anti-invention)."""
    haystack = re.sub(r"\s+", " ", plain(text))
    out = {**draft}
    out["skills"] = [s for s in draft.get("skills", []) if _appears(s.get("name"), haystack)]
    out["languages"] = [lang for lang in draft.get("languages", []) if _appears(lang.get("name"), haystack)]
    out["experiences"] = [e for e in draft.get("experiences", [])
                          if _appears(e.get("company"), haystack) or _appears(e.get("position"), haystack)]
    out["educations"] = [e for e in draft.get("educations", [])
                         if _appears(e.get("institution"), haystack) or _appears(e.get("degree"), haystack)]
    return out


def _valid_date(value) -> str | None:
    try:
        return date.fromisoformat(value).isoformat() if value else None
    except (TypeError, ValueError):
        return None


def clean_draft(draft: dict) -> dict:
    """Normalise le brouillon : longueurs maximales du profil, dates valides, niveaux connus."""
    out = empty_draft()
    out["domain"] = (draft.get("domain") or None) and str(draft["domain"])[:80]
    out["level"] = draft.get("level") if draft.get("level") in LEVELS else None
    seen = set()
    for skill in draft.get("skills", []):
        name = str(skill.get("name", "")).strip()[:60]
        if name and plain(name) not in seen:
            seen.add(plain(name))
            out["skills"].append({"name": name})
    for e in draft.get("experiences", []):
        out["experiences"].append({
            "position": str(e.get("position") or "").strip()[:100] or None,
            "company": (e.get("company") or "").strip()[:100] or None,
            "start_date": _valid_date(e.get("start_date")), "end_date": _valid_date(e.get("end_date")),
        })
    for e in draft.get("educations", []):
        out["educations"].append({
            "degree": str(e.get("degree") or "").strip()[:120] or None,
            "institution": (e.get("institution") or "").strip()[:120] or None,
            "start_date": _valid_date(e.get("start_date")), "end_date": _valid_date(e.get("end_date")),
        })
    seen = set()
    for lang in draft.get("languages", []):
        name = str(lang.get("name", "")).strip()[:40]
        if name and plain(name) not in seen:
            seen.add(plain(name))
            out["languages"].append({"name": name, "level": lang.get("level") if lang.get("level") in LANGUAGE_LEVELS else None})
    return out
