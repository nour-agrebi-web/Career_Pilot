"""Keejob (Tunisie).

Keejob publie lui-même des formats destinés aux programmes (https://www.keejob.com/llms.txt) :
- llms-full.txt : TOUTES les offres actives, une ligne par offre, en UNE requête ;
- une version Markdown de chaque offre (même URL, suffixe .md) ;
- un flux RSS des 15 dernières offres (utilisé en secours).
robots.txt n'interdit ni ces fichiers ni les pages d'offres (seulement /api/, les comptes, l'impression, la candidature…).
"""
from __future__ import annotations

import logging
import re
import xml.etree.ElementTree as ET

from ..categories import split_labels
from ..normalize import contract_type, strip_html, work_mode
from .base import Listing, Source

log = logging.getLogger(__name__)

LIST_URL = "https://www.keejob.com/llms-full.txt"
RSS_URL = "https://www.keejob.com/rss/jobs/"

# - [Titre](https://www.keejob.com/offres-emploi/250244/slug.md): Entreprise · Lieu · CDI, SIVP · Hybride · publiée le 2026-11-23
_LINE = re.compile(r"^\s*[-*]\s*\[(?P<title>.+?)\]\((?P<url>https?://[^)\s]+)\)\s*:?\s*(?P<meta>.*)$")
_ID = re.compile(r"/offres-emploi/(\d+)/")
_DATE = re.compile(r"publi[ée]+e? le (\d{4}-\d{2}-\d{2})", re.I)
_META = re.compile(r"^\s*[-*]\s*\**\s*([^:*]+?)\s*\**\s*:\s*\**\s*(.+?)\s*$")
_MODES = {"hybride", "télétravail", "teletravail", "sur site", "remote"}


def _canonical(url: str) -> str:
    return re.sub(r"\.md$", "/", url)


class Keejob(Source):
    name = "keejob"          # nom en ligne de commande
    label = "Keejob"     # valeur du champ source en base (comme les offres d'exemple du backend)

    def list_offers(self, client) -> Listing:
        try:
            resp = client.get(LIST_URL)
            listing = Listing(complete=True, not_modified=resp.not_modified)
            listing.items = self.parse_list(resp.body)
            if listing.items:
                return listing
            log.warning("keejob : llms-full.txt vide ou format inattendu, passage au flux RSS")
        except Exception as e:  # noqa: BLE001 - on veut toujours tenter le secours
            log.warning("keejob : llms-full.txt indisponible (%s), passage au flux RSS", e)
        resp = client.get(RSS_URL)
        return Listing(items=self.parse_rss(resp.body), complete=False, not_modified=resp.not_modified)

    # ----- liste complète -----
    def parse_list(self, body: str) -> list[dict]:
        items = []
        for line in body.splitlines():
            m = _LINE.match(line)
            if not m or not (mid := _ID.search(m["url"])):
                continue
            parts = [p.strip() for p in m["meta"].split("·") if p.strip()]
            date = _DATE.search(m["meta"])
            parts = [p for p in parts if not _DATE.search(p)]
            mode = next((p for p in parts if p.lower() in _MODES), None)
            parts = [p for p in parts if p != mode]
            company = parts[0] if parts else None
            city = parts[1] if len(parts) > 1 else None
            contract = parts[2] if len(parts) > 2 else None
            items.append({
                "source": self.label, "source_id": mid.group(1), "url": _canonical(m["url"]),
                "md_url": m["url"], "title": m["title"].strip(), "company": company, "city": city,
                "country": "Tunisie", "contract": contract_type(contract, m["title"]) or contract,
                "work_mode": work_mode(mode), "published_at": date.group(1) if date else None,
                "description": None, "detail_fetched": False, "insertable": True,
            })
        return items

    # ----- secours : RSS -----
    def parse_rss(self, body: str) -> list[dict]:
        items = []
        for it in ET.fromstring(body).iter("item"):
            link = (it.findtext("link") or "").strip()
            if not (mid := _ID.search(link)):
                continue
            desc = strip_html(it.findtext("description"))
            company = re.search(r"Entrep\w*\s*:\s*(.+)", desc)
            items.append({
                "source": self.label, "source_id": mid.group(1), "url": link,
                "md_url": link.rstrip("/") + ".md", "title": (it.findtext("title") or "").strip(),
                "company": company.group(1).strip() if company else None, "city": None,
                "country": "Tunisie", "contract": contract_type(desc), "work_mode": work_mode(desc),
                "published_at": _rfc822_date(it.findtext("pubDate")), "description": desc,
                # le RSS donne déjà la description complète : pas besoin de lire la page détail
                "detail_fetched": True, "insertable": True,
            })
        return items

    # ----- détail Markdown -----
    def detail_url(self, item: dict) -> str:
        return item.get("md_url") or item["url"].rstrip("/") + ".md"

    def parse_detail(self, item: dict, body: str) -> dict | None:
        meta, desc_lines, section = {}, [], None
        for line in body.splitlines():
            if line.startswith("# ") and "title_md" not in meta:
                meta["title_md"] = line[2:].strip()
                continue
            if line.startswith("## "):
                section = line[3:].strip().lower()
                continue
            if line.strip() == "---":
                section = "footer"
                continue
            if section is None and (m := _META.match(line)):
                meta[m.group(1).strip().lower()] = m.group(2).strip()
            elif section is None or not section.startswith(("postuler", "footer", "candidature")):
                # « ## Description », « ## Description du poste », « ## Missions »… ou pas de titre du tout :
                # tout ce qui n'est ni métadonnée, ni candidature, ni pied de page est gardé.
                desc_lines.append(line)
        description = re.sub(r"[*_`#>]", "", "\n".join(desc_lines)).strip()
        if not description:
            return None
        lieu = meta.get("lieu")
        return {
            **item,
            "title": meta.get("title_md") or item.get("title"),
            "company": meta.get("entreprise") or item.get("company"),
            "city": lieu or item.get("city"),
            "contract": contract_type(meta.get("type de contrat"), item.get("title")) or item.get("contract"),
            "published_at": _iso(meta.get("date de publication")) or item.get("published_at"),
            "expires_at": _iso(meta.get("date d'expiration")),
            # Rubrique du site (« Métiers ») : sert à la catégorie de l'offre (voir categories.py).
            "source_categories": split_labels(meta.get("métiers") or meta.get("métier")),
            "description": "\n".join(filter(None, [
                description,
                f"Expérience : {meta['expérience']}" if meta.get("expérience") else None,
                f"Langues : {meta['langues']}" if meta.get("langues") else None,
                f"Métiers : {meta['métiers']}" if meta.get("métiers") else None,
                "Niveau d'études : " + meta["niveau d'études"] if meta.get("niveau d'études") else None,
            ])),
            "detail_fetched": True,
        }


def _iso(value: str | None) -> str | None:
    """Accepte 2026-11-23 ou 23/11/2026."""
    if not value:
        return None
    if m := re.search(r"(\d{4})-(\d{2})-(\d{2})", value):
        return m.group(0)
    if m := re.search(r"(\d{1,2})[/.](\d{1,2})[/.](\d{4})", value):
        return f"{m.group(3)}-{int(m.group(2)):02d}-{int(m.group(1)):02d}"
    return None


def _rfc822_date(value: str | None) -> str | None:
    from email.utils import parsedate_to_datetime
    try:
        return parsedate_to_datetime(value).date().isoformat() if value else None
    except (TypeError, ValueError):
        return None
