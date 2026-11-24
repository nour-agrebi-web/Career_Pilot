"""Stockage MongoDB, dans la même base que le backend CareerPilot (« careerpilot » par défaut).

Collections :
  offers             partagée avec le backend. Mêmes champs que backend/seeds/offers_dev.py
                     (source, source_id, title, company, location {city, country}, type, work_mode,
                     description, publication_date, url, status), la catégorie (category, categories,
                     category_origin, source_categories : voir categories.py), plus les champs techniques du scraper
                     (content_hash, fingerprint, source_lastmod, detail_fetched, duplicate_of, first_seen, last_seen).
  offer_scores       un score par offre ET par version du profil, stocké même sous le seuil.
  scraper_http_cache dernier contenu reçu par URL, avec ETag / Last-Modified (requêtes conditionnelles).
  scraper_runs       statistiques de chaque exécution.
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Iterable

from pymongo import ASCENDING, DESCENDING, ReturnDocument

import re

from .categories import categorize, split_labels
from .enrich import detect_level, extract_skills
from pymongo.database import Database

ACTIVE, EXPIRED = "ACTIVE", "EXPIRED"     # valeurs de JobOfferStatus dans le backend

# Seules les offres écrites par ce scraper ont un content_hash : les offres d'exemple du backend
# (seeds/offers_dev.py) ne sont donc jamais modifiées ni désactivées par lui.
SCRAPED = {"content_hash": {"$exists": True}}


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _date(value: str | None) -> datetime | None:
    """« 2026-11-23 » -> datetime UTC, comme publication_date dans le backend."""
    if not value:
        return None
    try:
        return datetime.fromisoformat(value[:10]).replace(tzinfo=timezone.utc)
    except ValueError:
        return None


def to_doc(offer: dict) -> dict:
    """Offre normalisée par un adaptateur -> document au format de la collection offers du backend."""
    return {
        "source": offer["source"], "source_id": offer["source_id"], "url": offer["url"],
        "title": offer.get("title"), "company": offer.get("company"),
        # Chaîne vide plutôt que None : le matching du backend appelle .strip() sur la ville.
        "location": {"city": offer.get("city") or "", "country": offer.get("country") or ""},
        "type": offer.get("contract"), "work_mode": offer.get("work_mode"),
        "description": offer.get("description") or "",
        "publication_date": _date(offer.get("published_at")), "expires_at": _date(offer.get("expires_at")),
        # Indispensables au score du backend (30 points compétences, 15 points niveau) : voir enrich.py.
        "required_skills": extract_skills(offer.get("title"), offer.get("description")),
        "level": detect_level(offer.get("title"), offer.get("description"), offer.get("contract")),
        "source_categories": offer.get("source_categories") or [],
        **categorize(offer.get("source_categories"), offer.get("title"), offer.get("description")),
        "source_lastmod": offer.get("source_lastmod"),
        "detail_fetched": bool(offer.get("detail_fetched")),
        "content_hash": offer["content_hash"], "fingerprint": offer["fingerprint"],
    }


def _merge(new: dict, old: dict) -> dict:
    """Garde les valeurs déjà connues quand la nouvelle lecture ne les fournit pas (ligne de liste vs détail)."""
    out = {}
    for k, v in new.items():
        if k == "location":
            ol = old.get("location") or {}
            out[k] = {kk: v.get(kk) or ol.get(kk) or "" for kk in ("city", "country")}
        elif v in (None, "", []) and old.get(k) not in (None, ""):
            out[k] = old[k]
        else:
            out[k] = v
    return out


def _derive(doc: dict) -> dict:
    doc["required_skills"] = extract_skills(doc.get("title"), doc.get("description"))
    doc["level"] = detect_level(doc.get("title"), doc.get("description"), doc.get("type"))
    doc.update(categorize(doc.get("source_categories"), doc.get("title"), doc.get("description")))
    return doc


_KEEJOB_METIERS = re.compile(r"^M[ée]tiers?\s*:\s*(.+)$", re.M)
_TANIT_ID = re.compile(r"/job/(\d+)/")


class Storage:
    def __init__(self, db: Database):
        self.db = db
        self.offers = db.offers
        self.scores = db.offer_scores
        self.cache = db.scraper_http_cache
        self.runs = db.scraper_runs
        self.failures = db.scraper_failures     # pages détail en échec : abandonnées après MAX_FAILURES essais
        self.create_indexes()

    def create_indexes(self) -> None:
        # Même index unique que backend/app/db/mongo.py : (source, identifiant source).
        self.offers.create_index([("source", ASCENDING), ("source_id", ASCENDING)], unique=True)
        self.offers.create_index("url")            # déduplication par URL (non unique : les offres d'exemple
        self.offers.create_index("fingerprint")    # du backend partagent toutes l'URL d'accueil du site)
        self.scores.create_index([("offer_id", ASCENDING), ("profile_hash", ASCENDING)], unique=True)
        self.scores.create_index([("profile_hash", ASCENDING), ("score", DESCENDING)])
        self.cache.create_index("url", unique=True)
        self.failures.create_index("url", unique=True)

    def close(self) -> None:
        self.db.client.close()

    # ---------- cache HTTP ----------
    def cache_get(self, url: str):
        return self.cache.find_one({"url": url})

    def cache_put(self, url: str, body: str, etag: str | None, last_modified: str | None) -> None:
        self.cache.update_one({"url": url}, {"$set": {"body": body, "etag": etag, "last_modified": last_modified,
                                                      "fetched_at": _now()}}, upsert=True)

    def cache_touch(self, url: str) -> None:
        self.cache.update_one({"url": url}, {"$set": {"fetched_at": _now()}})

    # ---------- offres ----------
    def get_offer(self, source: str, source_id: str):
        return self.offers.find_one({"source": source, "source_id": source_id})

    def get_offer_by_url(self, url: str):
        return self.offers.find_one({"url": url, **SCRAPED})

    def upsert_offer(self, offer: dict) -> tuple[object, str]:
        """Insère ou met à jour une offre. Renvoie (_id, état) avec état = new | updated | unchanged.
        Déduplication : même (source, source_id) OU même URL normalisée = même offre."""
        now = _now()
        doc = to_doc(offer)
        old = self.get_offer(doc["source"], doc["source_id"]) or self.get_offer_by_url(doc["url"])
        if old is None:
            dup = self.offers.find_one({"fingerprint": doc["fingerprint"], "source": {"$ne": doc["source"]},
                                        "duplicate_of": None, **SCRAPED}, {"_id": 1})
            doc.update(status=ACTIVE, duplicate_of=dup["_id"] if dup else None, first_seen=now, last_seen=now)
            return self.offers.insert_one(doc).inserted_id, "new"

        merged = _merge(doc, old)
        _derive(merged)      # compétences et niveau recalculés sur le texte le plus complet connu
        merged["detail_fetched"] = doc["detail_fetched"] or bool(old.get("detail_fetched"))
        # Une simple ligne de liste ne remplace pas une offre déjà lue en détail.
        changed = doc["content_hash"] != old.get("content_hash") and (doc["detail_fetched"]
                                                                     or not old.get("detail_fetched"))
        merged["content_hash"] = doc["content_hash"] if changed else old["content_hash"]
        merged.update(status=ACTIVE, last_seen=now)
        self.offers.update_one({"_id": old["_id"]}, {"$set": merged})
        return old["_id"], "updated" if changed else "unchanged"

    def backfill_matching_fields(self) -> int:
        """Complète les offres déjà en base avant l'ajout de required_skills / level, et remplace une ville
        None par "" (sinon POST /matches/refresh du backend plante). Sans réseau ; ne touche que nos offres."""
        todo = self.offers.find({**SCRAPED, "$or": [{"level": {"$exists": False}}, {"location.city": None},
                                                     {"description": None}]})
        n = 0
        for doc in todo:
            loc = doc.get("location") or {}
            fix = _derive({"title": doc.get("title"), "description": doc.get("description") or "",
                           "type": doc.get("type")})
            fix.update(description=doc.get("description") or "",
                       location={"city": loc.get("city") or "", "country": loc.get("country") or ""})
            fix.pop("title"); fix.pop("type")
            self.offers.update_one({"_id": doc["_id"]}, {"$set": fix})
            n += 1
        return n

    def backfill_categories(self, all_offers: bool = False) -> dict:
        """Rattrape la catégorie des offres déjà en base (sans réseau). Rubrique du site retrouvée :
        - Keejob : ligne « Métiers : … » déjà recopiée dans la description ;
        - Tanitjobs : page de l'offre gardée dans le cache HTTP du scraper (scraper_http_cache).
        Sinon la catégorie est déduite du texte. `all_offers` recalcule aussi les offres déjà catégorisées."""
        from .sources.tanitjobs import find_jobposting, source_categories

        query = dict(SCRAPED) if all_offers else {**SCRAPED, "categories": {"$exists": False}}
        stats = {"site": 0, "deduite": 0, "sans_categorie": 0}
        for doc in self.offers.find(query):
            labels = doc.get("source_categories") or []
            if not labels and (m := _KEEJOB_METIERS.search(doc.get("description") or "")):
                labels = split_labels(m.group(1))
            if not labels and (m := _TANIT_ID.search(doc.get("url") or "")):
                page = self.cache_get(doc["url"]) or self.cache.find_one(
                    {"url": {"$regex": rf"tanitjobs\.com/job/{m.group(1)}/"}})
                if page and page.get("body"):
                    labels = source_categories(find_jobposting(page["body"]))
            fix = {"source_categories": labels, **categorize(labels, doc.get("title"), doc.get("description"))}
            self.offers.update_one({"_id": doc["_id"]}, {"$set": fix})
            stats[fix["category_origin"] or "sans_categorie"] += 1
        return stats

    MAX_FAILURES = 3

    def record_failure(self, url: str, reason: str) -> int:
        doc = self.failures.find_one_and_update(
            {"url": url}, {"$inc": {"count": 1}, "$set": {"reason": reason, "last_at": _now()}},
            upsert=True, return_document=ReturnDocument.AFTER)
        return doc["count"]

    def given_up(self, url: str) -> bool:
        doc = self.failures.find_one({"url": url}, {"count": 1})
        return bool(doc and doc["count"] >= self.MAX_FAILURES)

    def clear_failure(self, url: str) -> None:
        self.failures.delete_one({"url": url})

    def expire(self, source: str, source_id: str) -> None:
        """Page détail introuvable (404/410) : l'offre a été retirée du site."""
        self.offers.update_one({"source": source, "source_id": source_id, **SCRAPED}, {"$set": {"status": EXPIRED}})

    def mark_inactive(self, source: str, seen_ids: Iterable[str]) -> int:
        """Offres absentes de la liste complète du site : status EXPIRED (jamais supprimées)."""
        seen = list(seen_ids)
        if not seen:
            return 0
        res = self.offers.update_many({"source": source, "status": ACTIVE, "source_id": {"$nin": seen}, **SCRAPED},
                                      {"$set": {"status": EXPIRED}})
        return res.modified_count

    # ---------- scores ----------
    def offers_to_score(self, profile_hash: str) -> list[dict]:
        """Offres jamais scorées pour ce profil, ou dont le contenu a changé depuis le dernier score."""
        done = {s["offer_id"]: s["offer_hash"] for s in
                self.scores.find({"profile_hash": profile_hash}, {"offer_id": 1, "offer_hash": 1})}
        return [o for o in self.offers.find(SCRAPED) if done.get(o["_id"]) != o["content_hash"]]

    def save_score(self, offer_id, profile_hash: str, offer_hash: str, score: int, details: dict) -> None:
        self.scores.update_one(
            {"offer_id": offer_id, "profile_hash": profile_hash},
            {"$set": {"offer_hash": offer_hash, "score": score, "details": details, "scored_at": _now()}},
            upsert=True)

    def commit(self) -> None:
        """Rien à faire : chaque écriture MongoDB est déjà validée (gardé pour la lisibilité du pipeline)."""

    def matches(self, profile_hash: str, threshold: int, include_inactive: bool = False, limit: int = 50):
        """Le seuil n'est qu'un filtre de LECTURE : aucune requête réseau, aucun recalcul."""
        scores = list(self.scores.find({"profile_hash": profile_hash, "score": {"$gte": threshold}})
                      .sort("score", DESCENDING))
        offers = {o["_id"]: o for o in self.offers.find({"_id": {"$in": [s["offer_id"] for s in scores]}})}
        out = []
        for s in scores:
            o = offers.get(s["offer_id"])
            if not o or o.get("duplicate_of") is not None:
                continue
            if not include_inactive and o.get("status") != ACTIVE:
                continue
            out.append({**o, "score": s["score"], "details": s["details"]})
            if len(out) >= limit:
                break
        return out

    def score_histogram(self, profile_hash: str):
        return list(self.scores.aggregate([
            {"$match": {"profile_hash": profile_hash}},
            {"$group": {"_id": {"$multiply": [{"$floor": {"$divide": ["$score", 10]}}, 10]}, "n": {"$sum": 1}}},
            {"$sort": {"_id": -1}}]))

    def source_stats(self):
        return list(self.offers.aggregate([
            {"$match": SCRAPED},
            {"$group": {"_id": "$source", "n": {"$sum": 1},
                        "detail": {"$sum": {"$cond": ["$detail_fetched", 1, 0]}},
                        "actives": {"$sum": {"$cond": [{"$eq": ["$status", ACTIVE]}, 1, 0]}}}},
            {"$sort": {"_id": 1}}]))

    def category_counts(self):
        """Nombre d'offres actives par catégorie (une offre compte dans chacune de ses catégories)."""
        return list(self.offers.aggregate([
            {"$match": {**SCRAPED, "status": ACTIVE}},
            {"$unwind": {"path": "$categories", "preserveNullAndEmptyArrays": True}},
            {"$group": {"_id": "$categories", "n": {"$sum": 1}}},
            {"$sort": {"n": -1}}]))

    # ---------- exécutions ----------
    def start_run(self):
        return self.runs.insert_one({"started_at": _now()}).inserted_id

    def finish_run(self, run_id, stats: dict) -> None:
        self.runs.update_one({"_id": run_id}, {"$set": {"finished_at": _now(), "stats": stats}})

    def last_run(self):
        return self.runs.find_one({"stats": {"$exists": True}}, sort=[("started_at", DESCENDING)])
