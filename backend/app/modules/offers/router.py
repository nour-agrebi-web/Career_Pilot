from datetime import datetime, timezone
from enum import Enum
from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel
from pymongo import DESCENDING
from pymongo.database import Database
from app.core.ids import parse_id
from app.db.mongo import get_db
from app.modules.auth.deps import get_current_user
from app.modules.offers.matching import score
from app.modules.preferences.router import get_preferences
from app.modules.profile import service as profiles

# Collection "offers" : { _id, source, source_id, title, company, location {city, country}, type,
#   work_mode (sur_site | hybride | teletravail), level, language, description, required_skills[],
#   publication_date, url, status (JobOfferStatus) }
# Collection "matches" : { _id, user_id, offer_id, score, breakdown {semantic, skills, level, preferences},
#   reasons[], missing_skills[], status (MatchStatus), notified, created_at, updated_at }

router = APIRouter(tags=["offers"])

MATCH_NOT_FOUND = "Correspondance introuvable."


class MatchStatus(str, Enum):
    PENDING = "PENDING"      # nouvelle
    VIEWED = "VIEWED"        # consultée
    ACCEPTED = "ACCEPTED"    # sauvegardée
    REJECTED = "REJECTED"    # masquée (« Pas pour moi ») : plus jamais proposée


class MatchStatusIn(BaseModel):
    status: MatchStatus


def offer_out(offer: dict) -> dict:
    return {
        "id": str(offer["_id"]), "source": offer["source"], "title": offer["title"], "company": offer["company"],
        "location": offer.get("location"), "type": offer["type"], "work_mode": offer.get("work_mode"),
        "level": offer.get("level"), "description": offer.get("description", ""),
        "required_skills": offer.get("required_skills", []), "publication_date": offer.get("publication_date"),
        "url": offer.get("url"), "status": offer.get("status", "ACTIVE"),
    }


def match_out(match: dict, offer: dict) -> dict:
    return {
        "id": str(match["_id"]), "score": match["score"], "breakdown": match["breakdown"],
        "reasons": match["reasons"], "missing_skills": match["missing_skills"], "status": match["status"],
        "created_at": match["created_at"], "offer": offer_out(offer),
    }


# --- Offres (lecture seule pour le candidat : elles viennent des connecteurs) ---

@router.get("/offers")
def list_offers(type: str | None = None, q: str | None = None, _: dict = Depends(get_current_user),
                db: Database = Depends(get_db)):
    query: dict = {"status": "ACTIVE"}
    if type:
        query["type"] = type
    offers = db.offers.find(query).sort("publication_date", DESCENDING)
    result = [offer_out(o) for o in offers]
    if q:
        needle = q.lower()
        result = [o for o in result if needle in f"{o['title']} {o['company']} {o['description']}".lower()]
    return result


@router.get("/offers/{offer_id}")
def get_offer(offer_id: str, _: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    offer = db.offers.find_one({"_id": parse_id(offer_id, "Offre introuvable.")})
    if offer is None:
        raise HTTPException(404, "Offre introuvable.")
    return offer_out(offer)


# --- Correspondances (matches) de l'utilisateur connecté ---

@router.post("/matches/refresh")
def refresh_matches(user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    """Lance un cycle de matching pour l'utilisateur (normalement fait par le planificateur)."""
    profile = profiles.get_or_create(db, user["_id"])
    if not profile.get("validated"):
        raise HTTPException(409, "Validez votre profil pour recevoir des offres.")
    prefs = get_preferences(db, user["_id"])
    now = datetime.now(timezone.utc)
    created = 0
    for offer in db.offers.find({"status": "ACTIVE"}):
        result = score(profile, prefs, offer)
        if result is None or result.score < prefs["threshold"]:
            continue
        update = db.matches.update_one(
            {"user_id": user["_id"], "offer_id": offer["_id"]},
            {
                "$set": {"score": result.score, "breakdown": result.breakdown, "reasons": result.reasons,
                         "missing_skills": result.missing_skills, "updated_at": now},
                # Le statut (sauvegardée, masquée...) choisi par l'utilisateur est conservé.
                "$setOnInsert": {"status": MatchStatus.PENDING.value, "notified": False, "created_at": now},
            },
            upsert=True,
        )
        created += 1 if update.upserted_id else 0
    return {"new_matches": created}


@router.get("/matches")
def list_matches(
    status: MatchStatus | None = None,
    type: str | None = None,
    min_score: int | None = Query(default=None, ge=0, le=100),
    user: dict = Depends(get_current_user),
    db: Database = Depends(get_db),
):
    # US-3.2.4 : par défaut, seules les offres au-dessus du seuil actuel sont affichées.
    threshold = min_score if min_score is not None else get_preferences(db, user["_id"])["threshold"]
    query: dict = {"user_id": user["_id"], "score": {"$gte": threshold}}
    query["status"] = status.value if status else {"$ne": MatchStatus.REJECTED.value}
    matches = list(db.matches.find(query).sort("score", DESCENDING))
    offers = {o["_id"]: o for o in db.offers.find({"_id": {"$in": [m["offer_id"] for m in matches]}})}
    return [match_out(m, offers[m["offer_id"]]) for m in matches
            if m["offer_id"] in offers and (type is None or offers[m["offer_id"]]["type"] == type)]


def _owned_match(db: Database, match_id: str, user: dict) -> dict:
    match = db.matches.find_one({"_id": parse_id(match_id, MATCH_NOT_FOUND), "user_id": user["_id"]})
    if match is None:
        raise HTTPException(404, MATCH_NOT_FOUND)
    return match


@router.get("/matches/{match_id}")
def get_match(match_id: str, user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    match = _owned_match(db, match_id, user)
    return match_out(match, db.offers.find_one({"_id": match["offer_id"]}))


@router.put("/matches/{match_id}")
def update_match(match_id: str, data: MatchStatusIn, user: dict = Depends(get_current_user),
                 db: Database = Depends(get_db)):
    match = _owned_match(db, match_id, user)
    db.matches.update_one({"_id": match["_id"]},
                          {"$set": {"status": data.status.value, "updated_at": datetime.now(timezone.utc)}})
    match["status"] = data.status.value
    return match_out(match, db.offers.find_one({"_id": match["offer_id"]}))


@router.delete("/matches/{match_id}", status_code=204)
def hide_match(match_id: str, user: dict = Depends(get_current_user), db: Database = Depends(get_db)):
    """« Pas pour moi » : la correspondance est masquée et ne sera plus proposée (suppression logique)."""
    match = _owned_match(db, match_id, user)
    db.matches.update_one({"_id": match["_id"]}, {"$set": {"status": MatchStatus.REJECTED.value}})
