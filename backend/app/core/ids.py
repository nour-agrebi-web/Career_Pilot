from bson import ObjectId
from bson.errors import InvalidId
from fastapi import HTTPException


def parse_id(value: str, not_found: str = "Élément introuvable.") -> ObjectId:
    """Convertit un identifiant reçu dans l'URL ; un identifiant mal formé donne un 404."""
    try:
        return ObjectId(value)
    except (InvalidId, TypeError):
        raise HTTPException(404, not_found)
