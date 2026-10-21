import jwt
from bson import ObjectId
from bson.errors import InvalidId
from fastapi import Depends, HTTPException
from fastapi.security import OAuth2PasswordBearer
from pymongo.database import Database
from app.core.security import decode_token
from app.db.mongo import get_db
from app.modules.auth.models import Role

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/login")

def get_current_user(token: str = Depends(oauth2_scheme), db: Database = Depends(get_db)) -> dict:
    try:
        payload = decode_token(token, "access")
        user = db.users.find_one({"_id": ObjectId(payload["sub"])})
    except (jwt.InvalidTokenError, InvalidId):
        user = None
    if user is None:
        raise HTTPException(401, "Jeton invalide ou expiré.", headers={"WWW-Authenticate": "Bearer"})
    return user

def require_admin(user: dict = Depends(get_current_user)) -> dict:  # US-1.3.2
    if user["role"] != Role.ADMIN.value:
        raise HTTPException(403, "Accès réservé à l'administrateur.")
    return user
