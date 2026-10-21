import enum
from datetime import datetime, timezone
from bson import ObjectId

# Collections MongoDB utilisées par ce module :
#
# users          { _id: ObjectId, email (unique), password_hash, language, consent,
#                  consent_at, role, created_at }
# refresh_tokens { _id: ObjectId (= "jti" du jeton), user_id: ObjectId, expires_at, revoked }

class Role(str, enum.Enum):
    CANDIDATE = "candidate"
    ADMIN = "admin"

def new_user(email: str, password_hash: str, language: str) -> dict:
    now = datetime.now(timezone.utc)
    return {
        "_id": ObjectId(),
        "email": email,
        "password_hash": password_hash,
        "language": language,
        "consent": True,
        "consent_at": now,
        "role": Role.CANDIDATE.value,
        "created_at": now,
    }
