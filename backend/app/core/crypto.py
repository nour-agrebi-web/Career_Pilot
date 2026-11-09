import base64
import hashlib
from cryptography.fernet import Fernet, InvalidToken
from app.core.config import settings

# Exigence de sécurité du cahier des charges : CV chiffrés au repos (fichier et texte extrait).


def _fernet() -> Fernet:
    key = settings.cv_encryption_key
    if not key:
        # Dev : clé dérivée du secret JWT. En production, définir CV_ENCRYPTION_KEY.
        key = base64.urlsafe_b64encode(hashlib.sha256(settings.jwt_secret.encode()).digest()).decode()
    return Fernet(key.encode())


def encrypt(data: bytes) -> bytes:
    return _fernet().encrypt(data)


def decrypt(token: bytes) -> bytes:
    return _fernet().decrypt(token)


def encrypt_text(text: str) -> str:
    return encrypt(text.encode("utf-8")).decode("ascii")


def decrypt_text(token: str | None) -> str:
    if not token:
        return ""
    try:
        return decrypt(token.encode("ascii")).decode("utf-8")
    except InvalidToken:
        return ""
