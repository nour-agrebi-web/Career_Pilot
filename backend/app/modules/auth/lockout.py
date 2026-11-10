"""Blocage temporaire après trop d'échecs de connexion, compté par adresse e-mail.

Tous les 3 échecs, l'e-mail est bloqué : 1 minute la 1re fois, 3 minutes la 2e,
puis 5 minutes la 3e fois et les suivantes. Pendant un blocage, les tentatives sont
refusées sans être comptées. Une connexion réussie remet le compteur à zéro.
"""
import math
from datetime import datetime, timedelta, timezone
from pymongo import ReturnDocument
from pymongo.database import Database

# Nombre d'échecs qui déclenche un blocage.
MAX_FAILURES = 3
# Durée du 1er, du 2e et du 3e blocage ; la dernière vaut aussi pour les suivants.
LOCK_DURATIONS = (timedelta(minutes=1), timedelta(minutes=3), timedelta(minutes=5))


def now() -> datetime:
    return datetime.now(timezone.utc)


def lock_duration(lock_number: int) -> timedelta:
    """Durée du n-ième blocage (n >= 1)."""
    return LOCK_DURATIONS[min(lock_number, len(LOCK_DURATIONS)) - 1]


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
    if doc["failures"] % MAX_FAILURES:
        return 0
    duration = lock_duration(doc["failures"] // MAX_FAILURES)
    db.login_attempts.update_one({"_id": email}, {"$set": {"locked_until": now() + duration}})
    return int(duration.total_seconds())


def reset(db: Database, email: str) -> None:
    db.login_attempts.delete_one({"_id": email})
