"""Blocage temporaire après trop d'échecs de connexion, compté par adresse e-mail.

Les 3 premiers échecs sont normaux. Aux 4e et 5e échecs, l'e-mail est bloqué 3 minutes ;
à partir du 6e, 15 minutes. Une connexion réussie remet le compteur à zéro.
"""
import math
from datetime import datetime, timedelta, timezone
from pymongo import ReturnDocument
from pymongo.database import Database

# (nombre d'échecs atteint, durée du blocage), du palier le plus haut au plus bas.
LOCK_STEPS = ((6, timedelta(minutes=15)), (4, timedelta(minutes=3)))


def now() -> datetime:
    return datetime.now(timezone.utc)


def seconds_left(db: Database, email: str) -> int:
    """Secondes de blocage restantes pour cet e-mail (0 s'il n'est pas bloqué)."""
    doc = db.login_attempts.find_one({"_id": email}, {"locked_until": 1})
    locked_until = doc and doc.get("locked_until")
    if not locked_until:
        return 0
    return max(0, math.ceil((locked_until - now()).total_seconds()))


def record_failure(db: Database, email: str) -> int:
    """Compte un échec et renvoie la durée du blocage qu'il déclenche, en secondes (0 si aucun)."""
    doc = db.login_attempts.find_one_and_update(
        {"_id": email},
        {"$inc": {"failures": 1}, "$set": {"updated_at": now()}},
        upsert=True,
        return_document=ReturnDocument.AFTER,
    )
    duration = next((d for threshold, d in LOCK_STEPS if doc["failures"] >= threshold), None)
    if duration is None:
        return 0
    db.login_attempts.update_one({"_id": email}, {"$set": {"locked_until": now() + duration}})
    return int(duration.total_seconds())


def reset(db: Database, email: str) -> None:
    db.login_attempts.delete_one({"_id": email})
