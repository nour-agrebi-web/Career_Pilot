from datetime import datetime, timezone
from bson import ObjectId
from pymongo import ReturnDocument
from pymongo.database import Database

SECTIONS = ("skills", "experiences", "educations", "languages")


def get_or_create(db: Database, user_id: ObjectId) -> dict:
    """Renvoie le profil de l'utilisateur ; le crée vide au premier accès."""
    return db.profiles.find_one_and_update(
        {"user_id": user_id},
        {"$setOnInsert": {
            "_id": ObjectId(), "user_id": user_id, "domain": None, "level": None,
            "validated": False, "validated_at": None, "updated_at": datetime.now(timezone.utc),
            **{s: [] for s in SECTIONS},
        }},
        upsert=True,
        return_document=ReturnDocument.AFTER,
    )


def is_exploitable(profile: dict) -> bool:
    """Règle métier : au minimum des compétences, et une formation ou une expérience."""
    return bool(profile.get("skills")) and bool(profile.get("educations") or profile.get("experiences"))


def to_out(profile: dict) -> dict:
    return {
        "id": str(profile["_id"]),
        "domain": profile.get("domain"),
        "level": profile.get("level"),
        "validated": profile.get("validated", False),
        "updated_at": profile.get("updated_at"),
        **{s: profile.get(s, []) for s in SECTIONS},
    }
