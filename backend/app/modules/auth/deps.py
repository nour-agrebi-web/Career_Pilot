import uuid, jwt
from fastapi import Depends, HTTPException
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy.orm import Session
from app.core.security import decode_token
from app.db.session import get_db
from app.modules.auth.models import User, Role

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/login")

def get_current_user(token: str = Depends(oauth2_scheme), db: Session = Depends(get_db)) -> User:
    try:
        payload = decode_token(token, "access")
    except jwt.InvalidTokenError:
        raise HTTPException(401, "Jeton invalide ou expiré.", headers={"WWW-Authenticate": "Bearer"})
    user = db.get(User, uuid.UUID(payload["sub"]))
    if user is None:
        raise HTTPException(401, "Jeton invalide ou expiré.")
    return user

def require_admin(user: User = Depends(get_current_user)) -> User:  # US-1.3.2
    if user.role != Role.ADMIN:
        raise HTTPException(403, "Accès réservé à l'administrateur.")
    return user
