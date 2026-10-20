import uuid, jwt
from datetime import datetime, timedelta, timezone
from fastapi import APIRouter, Depends, HTTPException
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy import select
from sqlalchemy.orm import Session
from app.core.config import settings
from app.core.security import hash_password, verify_password, create_token, decode_token
from app.db.session import get_db
from app.modules.auth.models import User, RefreshToken
from app.modules.auth.schemas import RegisterIn, TokenPair, RefreshIn
from app.modules.auth.deps import get_current_user

router = APIRouter(tags=["auth"])

def issue_tokens(db: Session, user: User) -> TokenPair:
    days = timedelta(days=settings.refresh_token_days)
    rt = RefreshToken(user_id=user.id, expires_at=datetime.now(timezone.utc) + days)
    db.add(rt)
    db.commit()
    return TokenPair(
        access_token=create_token(user.id, "access", timedelta(minutes=settings.access_token_minutes)),
        refresh_token=create_token(user.id, "refresh", days, jti=rt.id),
    )

@router.post("/auth/register", status_code=201)
def register(data: RegisterIn, db: Session = Depends(get_db)):
    if not data.consent:  # US-1.1.2
        raise HTTPException(400, "Le consentement au traitement du CV est obligatoire.")
    email = data.email.lower()
    if db.scalar(select(User).where(User.email == email)):
        raise HTTPException(409, "Un compte existe déjà avec cet e-mail.")
    user = User(email=email, password_hash=hash_password(data.password), language=data.language,
                consent=True, consent_at=datetime.now(timezone.utc))
    db.add(user)
    db.commit()
    return {"id": str(user.id), "email": user.email}

@router.post("/auth/login", response_model=TokenPair)
def login(form: OAuth2PasswordRequestForm = Depends(), db: Session = Depends(get_db)):
    user = db.scalar(select(User).where(User.email == form.username.lower()))
    if user is None or not verify_password(form.password, user.password_hash):
        raise HTTPException(401, "E-mail ou mot de passe incorrect.")  # US-1.2.1 : message unique
    return issue_tokens(db, user)

@router.post("/auth/refresh", response_model=TokenPair)
def refresh(data: RefreshIn, db: Session = Depends(get_db)):
    try:
        payload = decode_token(data.refresh_token, "refresh")
    except jwt.InvalidTokenError:
        raise HTTPException(401, "Session expirée, reconnectez-vous.")
    rt = db.get(RefreshToken, uuid.UUID(payload["jti"]))
    if rt is None or rt.revoked:
        raise HTTPException(401, "Session expirée, reconnectez-vous.")
    rt.revoked = True  # rotation : chaque refresh token ne sert qu'une fois
    return issue_tokens(db, db.get(User, rt.user_id))

@router.post("/auth/logout", status_code=204)
def logout(data: RefreshIn, db: Session = Depends(get_db)):
    try:
        rt = db.get(RefreshToken, uuid.UUID(decode_token(data.refresh_token, "refresh")["jti"]))
        if rt:
            rt.revoked = True
            db.commit()
    except jwt.InvalidTokenError:
        pass

@router.get("/users/me")
def me(user: User = Depends(get_current_user)):
    return {"id": str(user.id), "email": user.email, "language": user.language, "role": user.role}
