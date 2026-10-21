import jwt
from datetime import datetime, timedelta, timezone
from bson import ObjectId
from bson.errors import InvalidId
from fastapi import APIRouter, Depends, HTTPException
from fastapi.security import OAuth2PasswordRequestForm
from pymongo.database import Database
from pymongo.errors import DuplicateKeyError
from app.core.config import settings
from app.core.security import hash_password, verify_password, create_token, decode_token
from app.db.mongo import get_db
from app.modules.auth.models import new_user
from app.modules.auth.schemas import RegisterIn, TokenPair, RefreshIn
from app.modules.auth.deps import get_current_user

router = APIRouter(tags=["auth"])

SESSION_EXPIRED = "Session expirée, reconnectez-vous."

def issue_tokens(db: Database, user_id: ObjectId) -> TokenPair:
    days = timedelta(days=settings.refresh_token_days)
    rt_id = db.refresh_tokens.insert_one({
        "user_id": user_id,
        "expires_at": datetime.now(timezone.utc) + days,
        "revoked": False,
    }).inserted_id
    return TokenPair(
        access_token=create_token(user_id, "access", timedelta(minutes=settings.access_token_minutes)),
        refresh_token=create_token(user_id, "refresh", days, jti=rt_id),
    )

def refresh_token_id(token: str) -> ObjectId:
    try:
        return ObjectId(decode_token(token, "refresh")["jti"])
    except (jwt.InvalidTokenError, InvalidId, KeyError):
        raise HTTPException(401, SESSION_EXPIRED)

@router.post("/auth/register", status_code=201)
def register(data: RegisterIn, db: Database = Depends(get_db)):
    if not data.consent:  # US-1.1.2
        raise HTTPException(400, "Le consentement au traitement du CV est obligatoire.")
    user = new_user(data.email.lower(), hash_password(data.password), data.language)
    try:
        db.users.insert_one(user)
    except DuplicateKeyError:  # index unique sur email
        raise HTTPException(409, "Un compte existe déjà avec cet e-mail.")
    return {"id": str(user["_id"]), "email": user["email"]}

@router.post("/auth/login", response_model=TokenPair)
def login(form: OAuth2PasswordRequestForm = Depends(), db: Database = Depends(get_db)):
    user = db.users.find_one({"email": form.username.lower()})
    if user is None or not verify_password(form.password, user["password_hash"]):
        raise HTTPException(401, "E-mail ou mot de passe incorrect.")  # US-1.2.1 : message unique
    return issue_tokens(db, user["_id"])

@router.post("/auth/refresh", response_model=TokenPair)
def refresh(data: RefreshIn, db: Database = Depends(get_db)):
    # Rotation : le jeton est révoqué au moment où il est utilisé (opération atomique),
    # il ne peut donc servir qu'une seule fois.
    rt = db.refresh_tokens.find_one_and_update(
        {"_id": refresh_token_id(data.refresh_token), "revoked": False},
        {"$set": {"revoked": True}},
    )
    if rt is None:
        raise HTTPException(401, SESSION_EXPIRED)
    return issue_tokens(db, rt["user_id"])

@router.post("/auth/logout", status_code=204)
def logout(data: RefreshIn, db: Database = Depends(get_db)):
    try:
        db.refresh_tokens.update_one({"_id": refresh_token_id(data.refresh_token)}, {"$set": {"revoked": True}})
    except HTTPException:
        pass

@router.get("/users/me")
def me(user: dict = Depends(get_current_user)):
    return {"id": str(user["_id"]), "email": user["email"], "language": user["language"], "role": user["role"]}
