"""Données de test pour le développement (base MongoDB "careerpilot").

Lancer depuis le dossier backend, environnement virtuel activé :
    python -m seeds.seed_dev

Tous les comptes ont le même mot de passe : motdepasse123
Le script peut être relancé : les comptes déjà présents ne sont pas dupliqués.
"""
from datetime import datetime, timezone
from bson import ObjectId
from app.core.security import hash_password
from app.db.mongo import create_indexes, get_db
from app.modules.auth.models import Role

PASSWORD = "motdepasse123"

USERS = [
    ("admin@careerpilot.tn", "fr", Role.ADMIN),
    ("nour.agrebi@etudiant.tn", "fr", Role.CANDIDATE),
    ("amine.hayouni@etudiant.tn", "fr", Role.CANDIDATE),
    ("dhia.battikh@etudiant.tn", "ar", Role.CANDIDATE),
    ("sarah.martin@example.fr", "en", Role.CANDIDATE),
]


def main() -> None:
    db = get_db()
    create_indexes(db)
    now = datetime.now(timezone.utc)
    for email, language, role in USERS:
        result = db.users.update_one(
            {"email": email},
            {"$setOnInsert": {
                "_id": ObjectId(),
                "email": email,
                "password_hash": hash_password(PASSWORD),
                "language": language,
                "consent": True,
                "consent_at": now,
                "role": role.value,
                "created_at": now,
            }},
            upsert=True,
        )
        print(("ajouté   " if result.upserted_id else "existant ") + f"{email} ({role.value})")
    print(f"{db.users.count_documents({})} utilisateurs dans la base « {db.name} ».")


if __name__ == "__main__":
    main()
