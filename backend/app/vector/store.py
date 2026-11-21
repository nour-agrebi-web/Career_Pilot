"""Base vectorielle Qdrant : stocke des vecteurs (embeddings) et retrouve les plus proches.

Deux modes, choisis par la configuration, avec exactement le même code au-dessus :
- local embarqué (par défaut, sans Docker) : QDRANT_PATH = un dossier ; Qdrant tourne dans le processus Python
  et écrit ses données dans ce dossier. Limite : un seul processus à la fois peut ouvrir le dossier.
- serveur : QDRANT_URL = http://localhost:6333 (Docker) ou l'URL d'un Qdrant Cloud (+ QDRANT_API_KEY).

Collections :
- CV_CHUNKS : morceaux des CV validés (compétences, expériences, formations), un point par morceau,
  avec dans le « payload » user_id, cv_id, section et le texte. Filtrables par user_id / cv_id.
- OFFERS : réservée à l'étape suivante (offres d'emploi vectorisées pour le matching).
"""
import threading
import warnings
from qdrant_client import QdrantClient, models

CV_CHUNKS = "cv_chunks"
OFFERS = "offers"

_lock = threading.Lock()
_client: QdrantClient | None = None


def get_client(settings) -> QdrantClient:
    """Client unique pour toute l'application (le mode local verrouille son dossier)."""
    global _client
    with _lock:
        if _client is None:
            if settings.qdrant_url:
                _client = QdrantClient(url=settings.qdrant_url, api_key=settings.qdrant_api_key or None)
            elif settings.qdrant_path == ":memory:":
                _client = QdrantClient(location=":memory:")  # tests
            else:
                _client = QdrantClient(path=settings.qdrant_path)
        return _client


def reset_client() -> None:
    """Ferme le client (tests, ou changement de configuration)."""
    global _client
    with _lock:
        if _client is not None:
            _client.close()
        _client = None


def ensure_collection(client: QdrantClient, name: str, size: int) -> None:
    """Crée la collection si besoin (distance cosinus) et les index des champs filtrés."""
    if client.collection_exists(name):
        return
    client.create_collection(name, vectors_config=models.VectorParams(size=size, distance=models.Distance.COSINE))
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")  # en mode local, Qdrant prévient que ces index sont inutiles
        for field in ("user_id", "cv_id"):
            client.create_payload_index(name, field, models.PayloadSchemaType.KEYWORD)


def _match(**fields) -> models.Filter:
    return models.Filter(must=[models.FieldCondition(key=k, match=models.MatchValue(value=v))
                               for k, v in fields.items()])


def delete_where(client: QdrantClient, name: str, **fields) -> None:
    if client.collection_exists(name):
        client.delete(name, points_selector=models.FilterSelector(filter=_match(**fields)))


def count_where(client: QdrantClient, name: str, **fields) -> int:
    if not client.collection_exists(name):
        return 0
    return client.count(name, count_filter=_match(**fields), exact=True).count


def upsert(client: QdrantClient, name: str, points: list[tuple[str, list[float], dict]]) -> None:
    """points = [(identifiant UUID, vecteur, payload)]."""
    client.upsert(name, points=[models.PointStruct(id=pid, vector=vec, payload=payload)
                                for pid, vec, payload in points])


def search(client: QdrantClient, name: str, vector: list[float], limit: int = 10, **fields) -> list:
    """Points les plus proches de `vector` (score = similarité cosinus), filtrés par les champs donnés."""
    if not client.collection_exists(name):
        return []
    return client.query_points(name, query=vector, limit=limit, query_filter=_match(**fields) if fields else None,
                               with_payload=True).points
