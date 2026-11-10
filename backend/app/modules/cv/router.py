from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4
from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from pymongo import DESCENDING
from pymongo.database import Database
from app.core.config import settings
from app.core.ids import parse_id
from app.db.mongo import get_db
from app.modules.auth.deps import get_current_user

# Collection MongoDB "cvs" :
# { _id, user_id, filename, file_path, size, version, status (CVStatus), active, created_at }

router = APIRouter(prefix="/cvs", tags=["cv"])

MAX_SIZE = 5 * 1024 * 1024          # Règle métier : 5 Mo maximum
MAX_VERSIONS = 10                   # Règle métier : 10 versions conservées au maximum
ALLOWED = {".pdf", ".docx"}         # Règle métier : PDF et DOCX uniquement
NOT_FOUND = "CV introuvable."


def to_out(cv: dict) -> dict:
    return {
        "id": str(cv["_id"]), "filename": cv["filename"], "size": cv["size"], "version": cv["version"],
        "status": cv["status"], "active": cv["active"], "created_at": cv["created_at"],
    }


def _owned(db: Database, cv_id: str, user: dict) -> dict:
    # US-1.3.1 : un CV d'un autre utilisateur est traité comme inexistant.
    cv = db.cvs.find_one({"_id": parse_id(cv_id, NOT_FOUND), "user_id": user["_id"]})
    if cv is None:
        raise HTTPException(404, NOT_FOUND)
    return cv


def _remove_file(cv: dict) -> None:
    Path(cv["file_path"]).unlink(missing_ok=True)


@router.post("", status_code=201)
async def upload(file: UploadFile = File(...), user: dict = Depends(get_current_user),
                 db: Database = Depends(get_db)):
    ext = Path(file.filename or "").suffix.lower()
    if ext not in ALLOWED:
        raise HTTPException(415, "Format non supporté : importez un fichier PDF ou DOCX.")
    content = await file.read(MAX_SIZE + 1)
    if len(content) > MAX_SIZE:
        raise HTTPException(413, "Fichier trop volumineux : 5 Mo maximum.")
    if not content:
        raise HTTPException(422, "Le fichier est vide.")

    folder = Path(settings.storage_dir) / "cvs" / str(user["_id"])
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / f"{uuid4().hex}{ext}"
    path.write_bytes(content)

    last = db.cvs.find_one({"user_id": user["_id"]}, sort=[("version", DESCENDING)])
    # Règle métier : un seul CV actif, les anciennes versions sont archivées.
    db.cvs.update_many({"user_id": user["_id"], "active": True}, {"$set": {"active": False}})
    cv = {
        "user_id": user["_id"], "filename": file.filename, "file_path": str(path), "size": len(content),
        "version": (last["version"] + 1) if last else 1, "status": "UPLOADED", "active": True,
        "created_at": datetime.now(timezone.utc),
    }
    cv["_id"] = db.cvs.insert_one(cv).inserted_id

    # Au-delà de 10 versions, la plus ancienne est supprimée (fichier compris).
    for old in db.cvs.find({"user_id": user["_id"]}).sort("version", DESCENDING).skip(MAX_VERSIONS):
        _remove_file(old)
        db.cvs.delete_one({"_id": old["_id"]})
    return to_out(cv)


@router.get("")
def list_cvs(user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    return [to_out(cv) for cv in db.cvs.find({"user_id": user["_id"]}).sort("version", DESCENDING)]


@router.get("/{cv_id}")
def get_cv(cv_id: str, user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    return to_out(_owned(db, cv_id, user))


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
