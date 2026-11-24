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
    # IA locale (Ollama, https://ollama.com) : aucune API payante, rien ne sort du PC.
    # ai_engine = "ollama" : le CV est structuré par le LLM local (repli sur les règles si Ollama ne répond pas).
    # ai_engine = "regles" : analyse par règles uniquement, sans LLM.
    ai_engine: str = "ollama"
    ollama_url: str = "http://localhost:11434"
    ollama_model: str = "llama3.2:3b"            # ou "mistral:7b" (meilleur, plus lent)
    ollama_embed_model: str = "nomic-embed-text"  # embeddings de 768 dimensions
    ollama_timeout_seconds: float = 120.0         # un LLM sur CPU est plus lent qu'une API en ligne
    # Base vectorielle Qdrant. Sans Docker : mode local embarqué, les vecteurs sont dans un dossier.
    # Pour passer plus tard à un serveur Qdrant (Docker ou cloud), il suffit de renseigner QDRANT_URL.
    qdrant_path: str = "storage/qdrant"
    qdrant_url: str = ""
    qdrant_api_key: str = ""
    ai_daily_quota: int = 5              # règle métier : 5 opérations IA par jour et par utilisateur
    # Chemin de tesseract.exe si Tesseract n'est pas dans le PATH (Windows), pour les CV scannés.
    tesseract_cmd: str = ""

settings = Settings()
