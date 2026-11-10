from datetime import datetime, timezone
from bson import ObjectId
from fastapi import APIRouter, Depends
from pymongo.database import Database
from app.db.mongo import get_db
from app.modules.auth.deps import get_current_user
from app.modules.preferences.schemas import DEFAULTS, PreferencesIn

router = APIRouter(prefix="/preferences", tags=["preferences"])


def get_preferences(db: Database, user_id: ObjectId) -> dict:
    """Préférences enregistrées, ou valeurs par défaut si l'utilisateur n'en a pas encore."""
    doc = db.preferences.find_one({"user_id": user_id}, {"_id": 0, "user_id": 0})
    return doc or {**DEFAULTS, "updated_at": None}


@router.get("")
def read(user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    return get_preferences(db, user["_id"])


@router.put("")
def replace(data: PreferencesIn, user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    doc = {**data.model_dump(mode="json"), "updated_at": datetime.now(timezone.utc)}
    db.preferences.update_one({"user_id": user["_id"]}, {"$set": doc}, upsert=True)
    return doc


@router.delete("", status_code=204)
def reset(user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    """Revient aux préférences par défaut."""
    db.preferences.delete_one({"user_id": user["_id"]})
