from enum import Enum
from pydantic import BaseModel, Field

# Collection MongoDB "preferences" (un document par utilisateur) :
# { _id, user_id (unique), job_types[], locations[{city, country}], remote_work, threshold,
#   notifications_enabled, updated_at }


class JobType(str, Enum):
    stage = "stage"
    emploi = "emploi"
    freelance = "freelance"
    temps_partiel = "temps_partiel"


class LocationIn(BaseModel):
    city: str = Field(min_length=1, max_length=60)
    country: str = Field(default="Tunisie", min_length=1, max_length=60)


class PreferencesIn(BaseModel):
    # Règle métier : au moins un type d'offre.
    job_types: list[JobType] = Field(min_length=1)
    locations: list[LocationIn] = Field(default_factory=list, max_length=10)
    remote_work: bool = True
    # US-3.2.3 : seuil entier de 50 à 90, 70 par défaut.
    threshold: int = Field(default=70, ge=50, le=90)
    notifications_enabled: bool = True


DEFAULTS = PreferencesIn(job_types=list(JobType)).model_dump(mode="json")
