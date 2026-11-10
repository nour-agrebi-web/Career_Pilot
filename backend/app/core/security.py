from datetime import datetime, timedelta, timezone
import jwt
from pwdlib import PasswordHash
from app.core.config import settings

_hasher = PasswordHash.recommended()  # Argon2

def hash_password(password: str) -> str:
    return _hasher.hash(password)

def verify_password(password: str, hashed: str) -> bool:
    return _hasher.verify(password, hashed)

def create_token(user_id, token_type: str, lifetime: timedelta, jti=None) -> str:
    payload = {"sub": str(user_id), "type": token_type,
               "exp": datetime.now(timezone.utc) + lifetime}
    if jti:
        payload["jti"] = str(jti)
    return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)

def decode_token(token: str, expected_type: str) -> dict:
    # lève jwt.InvalidTokenError si signature fausse, jeton expiré ou mauvais type
    payload = jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
    if payload.get("type") != expected_type:
        raise jwt.InvalidTokenError("type de jeton inattendu")
    return payload
