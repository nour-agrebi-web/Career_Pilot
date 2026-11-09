from pymongo import MongoClient
from pymongo.database import Database
from app.core.config import settings

# Un seul client pour toute l'application : il gère lui-même son pool de connexions.
client = MongoClient(settings.mongodb_url, tz_aware=True)

def get_db() -> Database:
    return client[settings.mongodb_db]

def create_indexes(db: Database) -> None:
    db.users.create_index("email", unique=True)
    db.refresh_tokens.create_index("user_id")
    # Index TTL : MongoDB supprime tout seul les refresh tokens expirés.
    db.refresh_tokens.create_index("expires_at", expireAfterSeconds=0)
    # Compteur d'échecs de connexion : oublié après 24 h sans nouvel échec.
    db.login_attempts.create_index("updated_at", expireAfterSeconds=24 * 3600)
    db.profiles.create_index("user_id", unique=True)
    db.preferences.create_index("user_id", unique=True)
    db.cvs.create_index([("user_id", 1), ("version", -1)])
    db.cvs.create_index([("user_id", 1), ("sha256", 1)])  # cache : un même fichier n'est pas réanalysé
    # Quota IA du jour (US-2.3.1) : un document par utilisateur et par jour, oublié après 2 jours.
    db.ai_usage.create_index("expires_at", expireAfterSeconds=0)
    db.offers.create_index([("source", 1), ("source_id", 1)], unique=True)  # règle : (source, identifiant source)
    db.matches.create_index([("user_id", 1), ("offer_id", 1)], unique=True)
