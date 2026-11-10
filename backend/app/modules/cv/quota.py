"""Quota d'opérations IA : 5 par jour et par utilisateur (US-2.3.1 et US-2.3.2).

Collection MongoDB "ai_usage" : { _id: "<user_id>:<AAAA-MM-JJ>", user_id, day, count, expires_at }.
Le compteur repart à zéro chaque jour (nouveau document) ; les anciens sont supprimés par un index TTL.
Décision sur le point Z11 du backlog : l'analyse initiale d'un CV compte comme une opération IA,
sauf si le même fichier a déjà été analysé (résultat repris du cache).
"""
from datetime import datetime, timedelta, timezone
from bson import ObjectId
from fastapi import HTTPException
from pymongo.database import Database
from pymongo.errors import DuplicateKeyError
from app.core.config import settings

QUOTA_MESSAGE = ("Quota atteint : {quota} opérations IA par jour. Vous pourrez relancer une analyse demain ; "
                 "votre CV et votre profil actuels ne changent pas.")


def _today() -> str:
    return datetime.now(timezone.utc).date().isoformat()


def used_today(db: Database, user_id: ObjectId) -> int:
    doc = db.ai_usage.find_one({"_id": f"{user_id}:{_today()}"})
    return doc["count"] if doc else 0


def status(db: Database, user_id: ObjectId) -> dict:
    used = used_today(db, user_id)
    return {"used": used, "limit": settings.ai_daily_quota, "remaining": max(settings.ai_daily_quota - used, 0)}


def ensure_available(db: Database, user_id: ObjectId) -> None:
    if used_today(db, user_id) >= settings.ai_daily_quota:
        raise HTTPException(429, QUOTA_MESSAGE.format(quota=settings.ai_daily_quota))


def consume(db: Database, user_id: ObjectId) -> None:
    """Décompte une opération, de façon atomique : refusée (429) si le quota du jour est atteint."""
    day = _today()
    try:
        db.ai_usage.update_one(
            {"_id": f"{user_id}:{day}", "count": {"$lt": settings.ai_daily_quota}},
            {"$inc": {"count": 1},
             "$setOnInsert": {"user_id": user_id, "day": day,
                              "expires_at": datetime.now(timezone.utc) + timedelta(days=2)}},
            upsert=True,
        )
    except DuplicateKeyError:
        # Le document du jour existe mais count >= quota : l'upsert tente un doublon de _id.
        raise HTTPException(429, QUOTA_MESSAGE.format(quota=settings.ai_daily_quota))
