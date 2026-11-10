import hashlib
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4
from fastapi import APIRouter, BackgroundTasks, Depends, File, HTTPException, UploadFile
from pydantic import ValidationError
from pymongo import DESCENDING
from pymongo.database import Database
from app.core import crypto
from app.core.config import settings
from app.core.ids import parse_id
from app.db.mongo import get_db
from app.modules.auth.deps import get_current_user
from app.modules.cv import quota
from app.modules.cv.analysis import AI_FAILED, copy_cached_result, run_analysis
from app.modules.profile import service as profile_service
from app.modules.profile.schemas import LEVELS_ALLOWED, SECTION_SCHEMAS, Section

# Collection MongoDB "cvs" :
# { _id, user_id, filename, file_path (fichier chiffré), size, sha256, version, status (CVStatus), active,
#   created_at, extracted_text (chiffré), extraction_method, engine, profile_draft, error_code, error,
#   analyzed_at, duration_ms, cached, applied_at }

router = APIRouter(prefix="/cvs", tags=["cv"])

MAX_SIZE = 5 * 1024 * 1024          # Règle métier : 5 Mo maximum
MAX_VERSIONS = 10                   # Règle métier : 10 versions conservées au maximum
ALLOWED = {".pdf", ".docx"}         # Règle métier : PDF et DOCX uniquement
NOT_FOUND = "CV introuvable."


def to_out(cv: dict) -> dict:
    return {
        "id": str(cv["_id"]), "filename": cv["filename"], "size": cv["size"], "version": cv["version"],
        "status": cv["status"], "active": cv["active"], "created_at": cv["created_at"],
        "error_code": cv.get("error_code"), "error": cv.get("error"),
        "applied": cv.get("applied_at") is not None,
    }


def _owned(db: Database, cv_id: str, user: dict) -> dict:
    # US-1.3.1 : un CV d'un autre utilisateur est traité comme inexistant.
    cv = db.cvs.find_one({"_id": parse_id(cv_id, NOT_FOUND), "user_id": user["_id"]})
    if cv is None:
        raise HTTPException(404, NOT_FOUND)
    return cv


def _remove_file(cv: dict) -> None:
    Path(cv["file_path"]).unlink(missing_ok=True)


def _missing_sections(draft: dict) -> list[str]:
    """US-2.2.4 : sections que l'analyse n'a pas su remplir, à compléter par le candidat."""
    return [s for s in ("educations", "experiences", "skills") if not draft.get(s)]


def _split_valid(draft: dict) -> tuple[dict[str, list[dict]], list[dict]]:
    """Sépare les éléments complets de ceux qui manquent d'une information obligatoire (ex. date de début)."""
    valid: dict[str, list[dict]] = {}
    incomplete: list[dict] = []
    for section in Section:
        valid[section.value] = []
        for item in draft.get(section.value, []):
            try:
                valid[section.value].append(SECTION_SCHEMAS[section].model_validate(item).model_dump(mode="json"))
            except ValidationError as e:
                fields = sorted({str(err["loc"][0]) for err in e.errors() if err["loc"]})
                incomplete.append({"section": section.value, "item": item, "missing_fields": fields})
    return valid, incomplete


@router.post("", status_code=201)
async def upload(background: BackgroundTasks, file: UploadFile = File(...), user: dict = Depends(get_current_user),
                 db: Database = Depends(get_db)):
    """US-2.1.1 : import d'un CV ; l'analyse est lancée en tâche de fond (statut UPLOADED puis PROCESSING)."""
    ext = Path(file.filename or "").suffix.lower()
    if ext not in ALLOWED:
        raise HTTPException(415, "Format non supporté : importez un fichier PDF ou DOCX.")
    content = await file.read(MAX_SIZE + 1)
    if len(content) > MAX_SIZE:
        raise HTTPException(413, "Fichier trop volumineux : 5 Mo maximum.")
    if not content:
        raise HTTPException(422, "Le fichier est vide.")

    # Cache : le même fichier déjà analysé n'est pas réanalysé et ne consomme pas de quota (US-2.3.1).
    sha256 = hashlib.sha256(content).hexdigest()
    cached = db.cvs.find_one({"user_id": user["_id"], "sha256": sha256,
                              "status": {"$in": ["PROCESSED", "ERROR"]}, "error_code": {"$ne": AI_FAILED}},
                             sort=[("version", DESCENDING)])
    if cached is None:
        # US-2.3.2 : au-delà du quota, rien n'est créé et le CV actif reste le même.
        quota.consume(db, user["_id"])

    folder = Path(settings.storage_dir) / "cvs" / str(user["_id"])
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / f"{uuid4().hex}{ext}.enc"
    path.write_bytes(crypto.encrypt(content))  # CV chiffré au repos

    last = db.cvs.find_one({"user_id": user["_id"]}, sort=[("version", DESCENDING)])
    # Règle métier : un seul CV actif, les anciennes versions sont archivées.
    db.cvs.update_many({"user_id": user["_id"], "active": True}, {"$set": {"active": False}})
    cv = {
        "user_id": user["_id"], "filename": file.filename, "file_path": str(path), "size": len(content),
        "sha256": sha256, "version": (last["version"] + 1) if last else 1, "status": "UPLOADED", "active": True,
        "created_at": datetime.now(timezone.utc),
    }
    cv["_id"] = db.cvs.insert_one(cv).inserted_id

    # Au-delà de 10 versions, la plus ancienne est supprimée (fichier compris).
    for old in db.cvs.find({"user_id": user["_id"]}).sort("version", DESCENDING).skip(MAX_VERSIONS):
        _remove_file(old)
        db.cvs.delete_one({"_id": old["_id"]})

    if cached is not None:
        copy_cached_result(db, cached, cv["_id"])
        return to_out(db.cvs.find_one({"_id": cv["_id"]}))
    background.add_task(run_analysis, db, cv["_id"])
    return to_out(cv)


@router.get("/quota")
def ai_quota(user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    """Opérations IA utilisées aujourd'hui (5 par jour)."""
    return quota.status(db, user["_id"])


@router.get("")
def list_cvs(user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    return [to_out(cv) for cv in db.cvs.find({"user_id": user["_id"]}).sort("version", DESCENDING)]


@router.get("/{cv_id}")
def get_cv(cv_id: str, user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    return to_out(_owned(db, cv_id, user))


@router.get("/{cv_id}/analysis")
def get_analysis(cv_id: str, user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    """Résultat de l'analyse : profil déduit, sections vides et éléments à compléter (US-2.2.1, US-2.2.4)."""
    cv = _owned(db, cv_id, user)
    draft = cv.get("profile_draft") if cv["status"] == "PROCESSED" else None
    result = {**to_out(cv), "engine": cv.get("engine"), "extraction_method": cv.get("extraction_method"),
              "duration_ms": cv.get("duration_ms"), "cached": cv.get("cached", False),
              "draft": draft, "missing_sections": [], "incomplete_items": []}
    if draft is not None:
        result["missing_sections"] = _missing_sections(draft)
        result["incomplete_items"] = _split_valid(draft)[1]
    return result


@router.post("/{cv_id}/analyze", status_code=202)
def analyze_again(cv_id: str, background: BackgroundTasks, user: dict = Depends(get_current_user),
                  db: Database = Depends(get_db)):
    """Relance l'analyse (après une erreur passagère par exemple). Compte comme une opération IA."""
    cv = _owned(db, cv_id, user)
    if cv["status"] in ("UPLOADED", "PROCESSING"):
        raise HTTPException(409, "L'analyse de ce CV est déjà en cours.")
    quota.consume(db, user["_id"])
    db.cvs.update_one({"_id": cv["_id"]}, {"$set": {"status": "UPLOADED", "cached": False}})
    background.add_task(run_analysis, db, cv["_id"])
    return to_out({**cv, "status": "UPLOADED"})


@router.post("/{cv_id}/apply-profile")
def apply_profile(cv_id: str, user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    """Copie le profil déduit dans le profil du candidat, qui doit ensuite le relire et le valider.

    Le profil repasse à « non validé » : le nouveau profil ne sert au matching qu'après validation (US-2.4.1).
    Les éléments incomplets (sans date de début, sans niveau de langue…) ne sont pas copiés : ils sont
    renvoyés dans `incomplete_items` pour que le candidat les complète lui-même (US-2.2.4).
    """
    cv = _owned(db, cv_id, user)
    if cv["status"] != "PROCESSED" or not cv.get("profile_draft"):
        raise HTTPException(409, "Ce CV n'a pas encore de profil déduit à appliquer.")
    draft = cv["profile_draft"]
    valid, incomplete = _split_valid(draft)
    profile = profile_service.get_or_create(db, user["_id"])
    now = datetime.now(timezone.utc)
    update = {s: [{"id": uuid4().hex, **item} for item in items] for s, items in valid.items()}
    update |= {"validated": False, "validated_at": None, "updated_at": now, "source_cv_id": cv["_id"]}
    if draft.get("domain"):
        update["domain"] = draft["domain"]
    if draft.get("level") in LEVELS_ALLOWED:
        update["level"] = draft["level"]
    db.profiles.update_one({"_id": profile["_id"]}, {"$set": update})
    db.cvs.update_one({"_id": cv["_id"]}, {"$set": {"applied_at": now}})
    return {
        "profile": profile_service.to_out(db.profiles.find_one({"_id": profile["_id"]})),
        "missing_sections": _missing_sections(valid),
        "incomplete_items": incomplete,
    }


@router.put("/{cv_id}/activate")
def activate(cv_id: str, user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    """Rend actif un CV archivé ; l'ancien CV actif est archivé."""
    cv = _owned(db, cv_id, user)
    db.cvs.update_many({"user_id": user["_id"], "active": True}, {"$set": {"active": False}})
    db.cvs.update_one({"_id": cv["_id"]}, {"$set": {"active": True}})
    return to_out({**cv, "active": True})


@router.delete("/{cv_id}", status_code=204)
def delete(cv_id: str, user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    cv = _owned(db, cv_id, user)
    _remove_file(cv)
    db.cvs.delete_one({"_id": cv["_id"]})
    if cv["active"]:
        # La version la plus récente restante devient le CV actif.
        latest = db.cvs.find_one({"user_id": user["_id"]}, sort=[("version", DESCENDING)])
        if latest:
            db.cvs.update_one({"_id": latest["_id"]}, {"$set": {"active": True}})
