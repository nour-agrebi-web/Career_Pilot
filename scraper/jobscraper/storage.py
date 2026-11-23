"""Stockage MongoDB, dans la même base que le backend CareerPilot (« careerpilot » par défaut).

Collections :
  offers             partagée avec le backend. Mêmes champs que backend/seeds/offers_dev.py
                     (source, source_id, title, company, location {city, country}, type, work_mode,
                     description, publication_date, url, status), plus les champs techniques du scraper
                     (content_hash, fingerprint, source_lastmod, detail_fetched, duplicate_of, first_seen, last_seen).
  offer_scores       un score par offre ET par version du profil, stocké même sous le seuil.
  scraper_http_cache dernier contenu reçu par URL, avec ETag / Last-Modified (requêtes conditionnelles).
  scraper_runs       statistiques de chaque exécution.
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Iterable

from pymongo import ASCENDING, DESCENDING
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
        "location": {"city": offer.get("city"), "country": offer.get("country")},
        "type": offer.get("contract"), "work_mode": offer.get("work_mode"),
        "description": offer.get("description"),
        "publication_date": _date(offer.get("published_at")), "expires_at": _date(offer.get("expires_at")),
        "required_skills": [],      # le backend s'en sert ; à remplir par une extraction de compétences (voir rapport)
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
            out[k] = {kk: v.get(kk) or ol.get(kk) for kk in ("city", "country")}
        elif v in (None, "", []) and old.get(k) not in (None, ""):
            out[k] = old[k]
        else:
            out[k] = v
    return out


class Storage:
    def __init__(self, db: Database):
        self.db = db
        self.offers = db.offers
        self.scores = db.offer_scores
        self.cache = db.scraper_http_cache
        self.runs = db.scraper_runs
        self.create_indexes()

    def create_indexes(self) -> None:
        # Même index unique que backend/app/db/mongo.py : (source, identifiant source).
        self.offers.create_index([("source", ASCENDING), ("source_id", ASCENDING)], unique=True)
        self.offers.create_index("url")            # déduplication par URL (non unique : les offres d'exemple
        self.offers.create_index("fingerprint")    # du backend partagent toutes l'URL d'accueil du site)
        self.scores.create_index([("offer_id", ASCENDING), ("profile_hash", ASCENDING)], unique=True)
        self.scores.create_index([("profile_hash", ASCENDING), ("score", DESCENDING)])
        self.cache.create_index("url", unique=True)

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
        merged["detail_fetched"] = doc["detail_fetched"] or bool(old.get("detail_fetched"))
        # Une simple ligne de liste ne remplace pas une offre déjà lue en détail.
        changed = doc["content_hash"] != old.get("content_hash") and (doc["detail_fetched"]
                                                                     or not old.get("detail_fetched"))
        merged["content_hash"] = doc["content_hash"] if changed else old["content_hash"]
        merged.update(status=ACTIVE, last_seen=now)
        self.offers.update_one({"_id": old["_id"]}, {"$set": merged})
        return old["_id"], "updated" if changed else "unchanged"

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

    # ---------- exécutions ----------
    def start_run(self):
        return self.runs.insert_one({"started_at": _now()}).inserted_id

    def finish_run(self, run_id, stats: dict) -> None:
        self.runs.update_one({"_id": run_id}, {"$set": {"finished_at": _now(), "stats": stats}})

    def last_run(self):
        return self.runs.find_one({"stats": {"$exists": True}}, sort=[("started_at", DESCENDING)])
