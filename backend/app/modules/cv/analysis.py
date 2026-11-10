"""Analyse d'un CV : extraction du texte, anonymisation, structuration en brouillon de profil.

Statuts (CVStatus) : UPLOADED -> PROCESSING -> PROCESSED ou ERROR.
L'analyse tourne en tâche de fond après l'import (BackgroundTasks de FastAPI). Quand Celery et Redis
seront en place (§4.2 du cahier des charges), `run_analysis` deviendra une tâche Celery sans changement.
"""
import logging
import time
from datetime import datetime, timezone
from pathlib import Path
from bson import ObjectId
from pymongo.database import Database
from app.core import crypto
from app.core.config import settings
from app.modules.cv.anonymize import anonymize
from app.modules.cv.extraction import ExtractionError, extract_text
from app.modules.cv.structuring import structure_text

logger = logging.getLogger(__name__)

AI_FAILED = "AI_FAILED"


def read_file(cv: dict) -> bytes:
    return crypto.decrypt(Path(cv["file_path"]).read_bytes())


def run_analysis(db: Database, cv_id: ObjectId) -> None:
    cv = db.cvs.find_one({"_id": cv_id})
    if cv is None:  # supprimé entre-temps
        return
    started = time.monotonic()
    db.cvs.update_one({"_id": cv_id}, {"$set": {"status": "PROCESSING", "error_code": None, "error": None}})
    try:
        extracted = extract_text(read_file(cv), Path(cv["filename"]).suffix.lower())
        user = db.users.find_one({"_id": cv["user_id"]}) or {}
        email = user.get("email", "")
        # Données personnelles retirées AVANT l'envoi au modèle (et avant tout stockage du brouillon).
        anonymized = anonymize(extracted.text, extra_values=[email, email.split("@")[0]])
        draft, engine = structure_text(anonymized, settings)
    except ExtractionError as e:
        _fail(db, cv_id, e.code, e.message)
        return
    except Exception:
        logger.exception("Analyse du CV %s impossible", cv_id)
        _fail(db, cv_id, AI_FAILED, "L'analyse de ce CV a échoué. Réessayez ou saisissez votre profil.")
        return
    db.cvs.update_one({"_id": cv_id}, {"$set": {
        "status": "PROCESSED",
        "extracted_text": crypto.encrypt_text(extracted.text),  # chiffré au repos, comme le fichier
        "extraction_method": extracted.method,
        "engine": engine,
        "profile_draft": draft,
        "analyzed_at": datetime.now(timezone.utc),
        "duration_ms": int((time.monotonic() - started) * 1000),
    }})


def _fail(db: Database, cv_id: ObjectId, code: str, message: str) -> None:
    # US-2.2.3 : statut ERROR, aucun profil n'est créé à partir de ce CV.
    db.cvs.update_one({"_id": cv_id}, {"$set": {
        "status": "ERROR", "error_code": code, "error": message, "profile_draft": None,
        "analyzed_at": datetime.now(timezone.utc),
    }})


def copy_cached_result(db: Database, source: dict, target_id: ObjectId) -> None:
    """Même fichier déjà analysé : on reprend le résultat sans refaire l'analyse ni décompter le quota."""
    fields = ("status", "extracted_text", "extraction_method", "engine", "profile_draft", "error_code", "error",
              "analyzed_at", "duration_ms")
    db.cvs.update_one({"_id": target_id}, {"$set": {f: source.get(f) for f in fields} | {"cached": True}})
