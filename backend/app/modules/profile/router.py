from datetime import datetime, timezone
from uuid import uuid4
from fastapi import APIRouter, Body, Depends, HTTPException
from pymongo.database import Database
from app.core.validation import validate_body
from app.db.mongo import get_db
from app.modules.auth.deps import get_current_user
from app.modules.profile import service
from app.modules.profile.schemas import SECTION_SCHEMAS, UNIQUE_FIELD, ProfileUpdate, Section

router = APIRouter(prefix="/profile", tags=["profile"])

NOT_FOUND = "Élément du profil introuvable."


def _now():
    return datetime.now(timezone.utc)


@router.get("")
def get_profile(user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    return service.to_out(service.get_or_create(db, user["_id"]))


@router.put("")
def update_profile(data: ProfileUpdate, user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    service.get_or_create(db, user["_id"])
    db.profiles.update_one(
        {"user_id": user["_id"]},
        {"$set": {"domain": data.domain, "level": data.level, "updated_at": _now()}},
    )
    return service.to_out(db.profiles.find_one({"user_id": user["_id"]}))


@router.post("/validate")
def validate_profile(user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    """US-2.x : le candidat valide le profil, qui devient la seule référence du matching."""
    profile = service.get_or_create(db, user["_id"])
    if not service.is_exploitable(profile):
        raise HTTPException(422, "Profil incomplet : ajoutez des compétences et une formation ou une expérience.")
    db.profiles.update_one({"_id": profile["_id"]}, {"$set": {"validated": True, "validated_at": _now()}})
    return service.to_out(db.profiles.find_one({"_id": profile["_id"]}))


@router.delete("", status_code=204)
def reset_profile(user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    db.profiles.delete_one({"user_id": user["_id"]})


# --- CRUD des éléments d'une section (compétences, expériences, formations, langues) ---

def _check_unique(profile: dict, section: Section, item: dict, ignore_id: str | None = None):
    field = UNIQUE_FIELD.get(section)
    if not field:
        return
    value = item[field].strip().lower()
    for existing in profile.get(section.value, []):
        if existing["id"] != ignore_id and existing[field].strip().lower() == value:
            raise HTTPException(409, f"« {item[field]} » figure déjà dans votre profil.")


@router.get("/{section}")
def list_items(section: Section, user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    return service.get_or_create(db, user["_id"]).get(section.value, [])


@router.post("/{section}", status_code=201)
def add_item(section: Section, body: dict = Body(...), user: dict = Depends(get_current_user),
             db: Database = Depends(get_db)):
    item = validate_body(SECTION_SCHEMAS[section], body).model_dump(mode="json")
    profile = service.get_or_create(db, user["_id"])
    _check_unique(profile, section, item)
    item = {"id": uuid4().hex, **item}
    db.profiles.update_one({"_id": profile["_id"]},
                           {"$push": {section.value: item}, "$set": {"updated_at": _now()}})
    return item


@router.put("/{section}/{item_id}")
def update_item(section: Section, item_id: str, body: dict = Body(...), user: dict = Depends(get_current_user),
                db: Database = Depends(get_db)):
    item = validate_body(SECTION_SCHEMAS[section], body).model_dump(mode="json")
    profile = service.get_or_create(db, user["_id"])
    _check_unique(profile, section, item, ignore_id=item_id)
    item = {"id": item_id, **item}
    result = db.profiles.update_one(
        {"_id": profile["_id"], f"{section.value}.id": item_id},
        {"$set": {f"{section.value}.$": item, "updated_at": _now()}},
    )
    if result.matched_count == 0:
        raise HTTPException(404, NOT_FOUND)
    return item


@router.delete("/{section}/{item_id}", status_code=204)
def delete_item(section: Section, item_id: str, user: dict = Depends(get_current_user),
                db: Database = Depends(get_db)):
    result = db.profiles.update_one(
        {"user_id": user["_id"], f"{section.value}.id": item_id},
        {"$pull": {section.value: {"id": item_id}}, "$set": {"updated_at": _now()}},
    )
    if result.matched_count == 0:
        raise HTTPException(404, NOT_FOUND)
