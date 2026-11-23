"""Tanitjobs (Tunisie).

Pas d'API ni de flux RSS public. Le chemin le plus léger autorisé par robots.txt :
1. le sitemap déclaré dans robots.txt liste chaque offre avec sa date de modification (lastmod) : UNE requête ;
2. on ne lit la page d'une offre que si elle est nouvelle ou si son lastmod a changé ;
3. sur la page, on lit les données structurées schema.org JobPosting (JSON-LD), que Tanitjobs publie pour
   Google Jobs : pas de dépendance à la mise en page HTML.
Les pages de recherche (paramètres searchID, keywords…) sont interdites par robots.txt et ne sont jamais appelées.
"""
from __future__ import annotations

import html
import json
import re
import xml.etree.ElementTree as ET

from ..normalize import contract_type, strip_html, work_mode
from .base import Listing, Source

SITEMAP_URL = "https://www.tanitjobs.com/sitemap.xml"
_NS = {"sm": "http://www.sitemaps.org/schemas/sitemap/0.9"}
_ID = re.compile(r"/job/(\d+)/")
_LD = re.compile(r'<script[^>]+application/ld\+json[^>]*>(.*?)</script>', re.S | re.I)
_META = r'<meta[^>]+(?:property|name)=["\']{}["\'][^>]+content=["\']([^"\']*)["\']'


class Tanitjobs(Source):
    name = "tanitjobs"          # nom en ligne de commande
    label = "TanitJobs"     # valeur du champ source en base (comme les offres d'exemple du backend)

    def list_offers(self, client) -> Listing:
        resp = client.get(SITEMAP_URL)
        return Listing(items=self.parse_sitemap(resp.body), complete=True, not_modified=resp.not_modified)

    def parse_sitemap(self, body: str) -> list[dict]:
        items = []
        root = ET.fromstring(body.strip())
        for url in root.findall("sm:url", _NS):
            loc = (url.findtext("sm:loc", namespaces=_NS) or "").strip()
            if not (m := _ID.search(loc)):
                continue        # pages statiques, aide, blog : ignorées
            items.append({"source": self.label, "source_id": m.group(1), "url": loc,
                          "source_lastmod": (url.findtext("sm:lastmod", namespaces=_NS) or "").strip() or None,
                          "insertable": False})
        return items

    def parse_detail(self, item: dict, body: str) -> dict | None:
        job = _find_jobposting(body)
        if job:
            org = job.get("hiringOrganization") or {}
            loc = job.get("jobLocation") or {}
            loc = loc[0] if isinstance(loc, list) and loc else loc
            addr = (loc.get("address") if isinstance(loc, dict) else None) or {}
            emp = job.get("employmentType")
            emp = ", ".join(emp) if isinstance(emp, list) else emp
            desc = strip_html(html.unescape(job.get("description") or ""))
            return {
                **item,
                "title": html.unescape(job.get("title") or "").strip(),
                "company": org.get("name") if isinstance(org, dict) else str(org),
                "city": addr.get("addressLocality") or addr.get("addressRegion"),
                "country": _country(addr.get("addressCountry")),
                "contract": contract_type(emp, job.get("title")),
                "work_mode": "teletravail" if job.get("jobLocationType") == "TELECOMMUTE" else work_mode(desc),
                "published_at": (job.get("datePosted") or "")[:10] or None,
                "expires_at": (job.get("validThrough") or "")[:10] or None,
                "description": desc,
                "detail_fetched": True,
            }
        # Secours si la page n'a pas de JSON-LD : balises meta standard (titre et résumé seulement).
        title = _meta(body, "og:title") or _first(r"<h1[^>]*>(.*?)</h1>", body)
        if not title:
            return None
        return {**item, "title": strip_html(title), "description": _meta(body, "description") or
                _meta(body, "og:description"), "country": "Tunisie", "detail_fetched": True}


def _find_jobposting(body: str) -> dict | None:
    for block in _LD.findall(body):
        try:
            data = json.loads(block.strip())
        except json.JSONDecodeError:
            continue
        stack = [data]
        while stack:
            node = stack.pop()
            if isinstance(node, list):
                stack.extend(node)
            elif isinstance(node, dict):
                t = node.get("@type")
                if t == "JobPosting" or (isinstance(t, list) and "JobPosting" in t):
                    return node
                stack.extend(v for k, v in node.items() if k == "@graph")
    return None


def _meta(body: str, name: str) -> str | None:
    m = re.search(_META.format(re.escape(name)), body, re.I)
    return html.unescape(m.group(1)).strip() if m else None


def _first(pattern: str, body: str) -> str | None:
    m = re.search(pattern, body, re.S | re.I)
    return m.group(1) if m else None


def _country(value) -> str | None:
    if isinstance(value, dict):
        value = value.get("name")
    return {"TN": "Tunisie", "FR": "France"}.get(value, value) if value else "Tunisie"
