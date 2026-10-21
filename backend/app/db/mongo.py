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
