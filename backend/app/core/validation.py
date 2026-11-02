from pydantic import BaseModel, ValidationError
from fastapi import HTTPException


def validate_body(model: type[BaseModel], body: dict) -> BaseModel:
    """Valide un corps JSON avec un schéma choisi à l'exécution (ex. selon la section du profil).

    Le premier problème est renvoyé dans `detail` sous forme de texte, comme les autres erreurs
    de l'API, pour que l'application puisse l'afficher tel quel.
    """
    try:
        return model.model_validate(body)
    except ValidationError as e:
        first = e.errors()[0]
        field = ".".join(str(p) for p in first["loc"]) or "corps"
        raise HTTPException(422, f"Champ « {field} » invalide : {first['msg']}")
