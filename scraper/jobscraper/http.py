"""Client HTTP « poli » : robots.txt, pause par hôte, cache conditionnel (ETag / Last-Modified), reprises.

Bibliothèque standard uniquement (urllib) pour que le script tourne partout sans installation.
"""
from __future__ import annotations

import gzip
import logging
import time
import urllib.error
import urllib.request
import urllib.robotparser
from dataclasses import dataclass
from email.utils import parsedate_to_datetime
from urllib.parse import urlsplit

from .storage import Storage

log = logging.getLogger(__name__)

USER_AGENT = "CareerPilotBot/0.1 (projet etudiant; collecte d'offres d'emploi a faible frequence)"


class RobotsDisallowed(Exception):
    """L'URL est interdite par le robots.txt du site : on ne la demande pas."""


@dataclass
class Response:
    url: str
    status: int            # 200, ou 304 si le serveur confirme que rien n'a changé
    body: str
    from_cache: bool       # True si le contenu vient du cache local (304 ou cache frais)
    not_modified: bool     # True si le serveur a répondu 304


class PoliteClient:
    def __init__(self, storage: Storage, delay: float = 2.0, timeout: float = 20.0,
                 max_retries: int = 3, cache_ttl: float = 0.0, opener=None, sleep=time.sleep,
                 clock=time.monotonic):
        self.storage = storage
        self.delay = delay              # pause minimale entre deux requêtes vers le même hôte
        self.timeout = timeout
        self.max_retries = max_retries
        self.cache_ttl = cache_ttl      # si > 0 : réutilise le cache sans requête pendant ce délai
        self._opener = opener or urllib.request.build_opener()
        self._sleep = sleep
        self._clock = clock
        self._last_hit: dict[str, float] = {}
        self._robots: dict[str, urllib.robotparser.RobotFileParser | None] = {}
        self.requests_sent = 0          # compteur pour le rapport d'exécution

    # ---------- robots.txt ----------
    def allowed(self, url: str) -> bool:
        parts = urlsplit(url)
        base = f"{parts.scheme}://{parts.netloc}"
        if base not in self._robots:
            rp = urllib.robotparser.RobotFileParser()
            try:
                body = self._raw_get(base + "/robots.txt", {}).body
                rp.parse(body.splitlines())
            except urllib.error.HTTPError as e:
                if e.code in (401, 403):
                    rp.disallow_all = True      # même règle que la bibliothèque standard
                else:
                    rp.allow_all = True         # 404 : pas de robots.txt, tout est permis
            except Exception as e:              # réseau : prudence, on n'y va pas
                log.warning("robots.txt illisible pour %s (%s) : hôte ignoré", base, e)
                rp.disallow_all = True
            self._robots[base] = rp
        return self._robots[base].can_fetch(USER_AGENT, url)

    # ---------- requête principale ----------
    def get(self, url: str) -> Response:
        if not self.allowed(url):
            raise RobotsDisallowed(url)
        cached = self.storage.cache_get(url)
        if cached and self.cache_ttl and time.time() - cached["fetched_at"] < self.cache_ttl:
            return Response(url, 200, cached["body"], from_cache=True, not_modified=False)

        headers = {}
        if cached:                       # requête conditionnelle : le serveur peut répondre 304 sans corps
            if cached["etag"]:
                headers["If-None-Match"] = cached["etag"]
            if cached["last_modified"]:
                headers["If-Modified-Since"] = cached["last_modified"]
        try:
            resp = self._raw_get(url, headers)
        except urllib.error.HTTPError as e:
            if e.code == 304 and cached:
                self.storage.cache_touch(url)
                return Response(url, 304, cached["body"], from_cache=True, not_modified=True)
            raise
        self.storage.cache_put(url, resp.body, resp.etag, resp.last_modified)
        return Response(url, resp.status, resp.body, from_cache=False, not_modified=False)

    # ---------- bas niveau : pause, reprises, gzip ----------
    @dataclass
    class _Raw:
        status: int
        body: str
        etag: str | None
        last_modified: str | None

    def _wait_for_host(self, host: str) -> None:
        last = self._last_hit.get(host)
        if last is not None:
            remaining = self.delay - (self._clock() - last)
            if remaining > 0:
                self._sleep(remaining)
        self._last_hit[host] = self._clock()

    def _raw_get(self, url: str, extra_headers: dict) -> "PoliteClient._Raw":
        host = urlsplit(url).netloc
        headers = {"User-Agent": USER_AGENT, "Accept-Encoding": "gzip", **extra_headers}
        attempt = 0
        while True:
            self._wait_for_host(host)
            self.requests_sent += 1
            req = urllib.request.Request(url, headers=headers)
            try:
                with self._opener.open(req, timeout=self.timeout) as r:
                    raw = r.read()
                    if (r.headers.get("Content-Encoding") or "").lower() == "gzip":
                        raw = gzip.decompress(raw)
                    charset = r.headers.get_content_charset() or "utf-8"
                    return self._Raw(r.status, raw.decode(charset, errors="replace"),
                                     r.headers.get("ETag"), r.headers.get("Last-Modified"))
            except urllib.error.HTTPError as e:
                if e.code in (429, 500, 502, 503, 504) and attempt < self.max_retries:
                    wait = _retry_after(e.headers.get("Retry-After")) or (2 ** attempt) * 5
                    log.info("HTTP %s sur %s : nouvel essai dans %.0f s", e.code, url, wait)
                    self._sleep(wait)
                    attempt += 1
                    continue
                raise
            except urllib.error.URLError as e:
                if attempt < self.max_retries:
                    self._sleep((2 ** attempt) * 5)
                    attempt += 1
                    log.info("Erreur réseau sur %s (%s) : nouvel essai", url, e.reason)
                    continue
                raise


def _retry_after(value: str | None) -> float | None:
    if not value:
        return None
    try:
        return min(float(value), 300.0)
    except ValueError:
        try:
            return max(0.0, min(parsedate_to_datetime(value).timestamp() - time.time(), 300.0))
        except Exception:
            return None
