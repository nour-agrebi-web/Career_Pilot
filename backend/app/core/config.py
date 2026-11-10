from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")
    mongodb_url: str = "mongodb://localhost:27017"
    mongodb_db: str = "careerpilot"
    mongodb_test_db: str = "careerpilot_test"
    jwt_secret: str
    jwt_algorithm: str = "HS256"
    access_token_minutes: int = 15
    refresh_token_days: int = 7
    # Délai max pour joindre MongoDB avant de renvoyer une erreur 503 (ms).
    mongodb_timeout_ms: int = 5000
    # Dossier des CV importés (ignoré par Git : données personnelles).
    storage_dir: str = "storage"
    # Clé Fernet de chiffrement des CV au repos. Vide : dérivée de JWT_SECRET (pratique en dev,
    # mais en production il faut une clé dédiée). Générer avec :
    # python -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())"
    cv_encryption_key: str = ""
    # Structuration du CV par un LLM (Claude). Vide : analyse locale par règles, sans IA externe.
    anthropic_api_key: str = ""
    ai_model: str = "claude-opus-5-5"
    ai_timeout_seconds: float = 25.0     # l'analyse complète doit rester sous 30 s
    ai_daily_quota: int = 5              # règle métier : 5 opérations IA par jour et par utilisateur
    # Chemin de tesseract.exe si Tesseract n'est pas dans le PATH (Windows), pour les CV scannés.
    tesseract_cmd: str = ""

settings = Settings()
