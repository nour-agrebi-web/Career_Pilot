"""Faux réseau pour les tests : sert les fixtures, gère ETag/304, compte les requêtes, peut simuler des erreurs."""
from __future__ import annotations

import email.message
import hashlib
import io
import urllib.error
from pathlib import Path

FIX = Path(__file__).parent / "fixtures"

ROUTES = {
    "https://www.keejob.com/robots.txt": "keejob_robots.txt",
    "https://www.keejob.com/llms-full.txt": "keejob_llms_full.txt",
    "https://www.keejob.com/rss/jobs/": "keejob_rss.xml",
    "https://www.tanitjobs.com/robots.txt": "tanitjobs_robots.txt",
    "https://www.tanitjobs.com/sitemap.xml": "tanitjobs_sitemap.xml",
}
for i in range(900001, 900005):
    ROUTES[f"__keejob_{i}"] = f"keejob_{i}.md"
for i in range(700001, 700004):
    ROUTES[f"__tanit_{i}"] = f"tanitjobs_{i}.html"


class _Resp(io.BytesIO):
    def __init__(self, body: bytes, headers: email.message.Message, status=200):
        super().__init__(body)
        self.headers, self.status = headers, status

    def __enter__(self):
        return self

    def __exit__(self, *a):
        self.close()


class FakeWeb:
    def __init__(self):
        self.pages: dict[str, str] = {}
        for url, name in ROUTES.items():
            self.pages[url] = (FIX / name).read_text(encoding="utf-8")
        self.errors: dict[str, list[int]] = {}   # url -> codes HTTP à renvoyer d'abord
        self.log: list[tuple[str, dict]] = []
        self.offline = False

    def resolve(self, url: str) -> str | None:
        if url in self.pages:
            return url
        import re
        if m := re.search(r"keejob\.com/offres-emploi/(\d+)/.*\.md$", url):
            return f"__keejob_{m.group(1)}"
        if m := re.search(r"tanitjobs\.com/job/(\d+)/", url):
            return f"__tanit_{m.group(1)}"
        return None

    def set_page(self, url_or_key: str, body: str):
        self.pages[url_or_key] = body

    def requests_to(self, fragment: str) -> int:
        return sum(fragment in u for u, _ in self.log)

    def open(self, req, timeout=None):
        url = req.full_url
        headers = dict(req.header_items())
        if self.offline:
            raise AssertionError(f"Requête réseau interdite dans ce test : {url}")
        self.log.append((url, headers))
        if self.errors.get(url):
            code = self.errors[url].pop(0)
            h = email.message.Message()
            if code == 429:
                h["Retry-After"] = "7"
            raise urllib.error.HTTPError(url, code, "erreur simulée", h, io.BytesIO(b""))
        key = self.resolve(url)
        if key is None or key not in self.pages:
            raise urllib.error.HTTPError(url, 404, "Not Found", email.message.Message(), io.BytesIO(b""))
        body = self.pages[key].encode()
        etag = '"' + hashlib.md5(body).hexdigest() + '"'
        h = email.message.Message()
        h["Content-Type"] = "text/plain; charset=utf-8"
        h["ETag"] = etag
        if headers.get("If-none-match") == etag:
            raise urllib.error.HTTPError(url, 304, "Not Modified", h, io.BytesIO(b""))
        return _Resp(body, h)
