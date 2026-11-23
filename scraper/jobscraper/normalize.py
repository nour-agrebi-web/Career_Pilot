"""Normalisation commune : texte, URL, empreintes, contrat, mode de travail."""
from __future__ import annotations

import hashlib
import html
import re
import unicodedata
from urllib.parse import urlsplit, urlunsplit

_TAG = re.compile(r"<[^>]+>")
_WS = re.compile(r"\s+")


def strip_html(text: str | None) -> str:
    if not text:
        return ""
    text = re.sub(r"(?i)<br\s*/?>|</p>|</li>|</h\d>", "\n", text)
    text = html.unescape(_TAG.sub(" ", text))
    return "\n".join(_WS.sub(" ", line).strip() for line in text.splitlines() if line.strip())


def fold(text: str | None) -> str:
    """Minuscules, sans accents, espaces réduits : « Développeur  Kotlin » -> « developpeur kotlin »."""
    if not text:
        return ""
    text = unicodedata.normalize("NFKD", text)
    text = "".join(c for c in text if not unicodedata.combining(c))
    return _WS.sub(" ", text.lower()).strip()


def normalize_url(url: str) -> str:
    """Hôte en minuscules, sans paramètres de suivi ni fragment, avec « / » final : une offre = une URL."""
    p = urlsplit(url.strip())
    path = p.path or "/"
    if not path.endswith("/") and not path.rsplit("/", 1)[-1].count("."):
        path += "/"
    return urlunsplit(("https", p.netloc.lower(), path, "", ""))


def sha(*parts: str | None) -> str:
    return hashlib.sha256("\x1f".join(fold(p) for p in parts).encode()).hexdigest()[:16]


def finalize(offer: dict) -> dict:
    """Ajoute URL normalisée, empreinte de contenu et empreinte de déduplication inter-sites."""
    offer["url"] = normalize_url(offer["url"])
    offer["content_hash"] = sha(offer.get("title"), offer.get("company"), offer.get("city"),
                                offer.get("contract"), offer.get("description"))
    offer["fingerprint"] = sha(offer.get("title"), offer.get("company"), offer.get("city"))
    return offer


_CONTRACTS = [("stage", "stage"), ("pfe", "stage"), ("sivp", "emploi"), ("cdi", "emploi"),
              ("cdd", "emploi"), ("freelance", "freelance"), ("independant", "freelance"),
              ("temps partiel", "temps_partiel"), ("part_time", "temps_partiel"),
              ("full_time", "emploi"), ("intern", "stage")]


def contract_type(*texts: str | None) -> str | None:
    """Ramène le contrat aux types de CareerPilot : stage, emploi, freelance, temps_partiel."""
    t = fold(" ".join(x for x in texts if x))
    for key, value in _CONTRACTS:
        if re.search(rf"(?<![a-z]){re.escape(key)}(?![a-z])", t):
            return value
    return None


def work_mode(*texts: str | None) -> str | None:
    t = fold(" ".join(x for x in texts if x))
    if "teletravail" in t or "remote" in t or "telecommute" in t:
        return "teletravail"
    if "hybride" in t or "hybrid" in t:
        return "hybride"
    if "sur site" in t or "presentiel" in t:
        return "sur_site"
    return None
