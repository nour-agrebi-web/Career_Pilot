"""Vectorisation des offres d'emploi et similarité sémantique profil ↔ offres (Epic 4).

Chaque offre active devient un vecteur (nomic-embed-text, via Ollama) rangé dans la collection Qdrant
`offers`, avec offer_id, source et source_id dans le payload. Un point par offre, identifiant stable.

Seulement les offres nouvelles ou modifiées : on garde dans MongoDB (offers.vector_index) l'empreinte du
texte vectorisé et du modèle. Le texte est construit à partir du titre, des compétences et de la description,
c'est-à-dire du contenu que résume aussi le content_hash du scraper : une offre dont le contenu change change
d'empreinte et est revectorisée, les autres ne coûtent rien. Les offres expirées sont retirées de Qdrant.

Le matching (router.refresh_matches) appelle `semantic_scores` : il vectorise ce qui manque, puis compare le
profil validé (une requête « search_query: ») à toutes les offres avec store.search. Si Ollama ou Qdrant ne
répond pas, `semantic_scores` renvoie None et le score retombe sur le recouvrement de mots-clés, sans erreur.

Le scraper n'a pas besoin d'Ollama : il écrit les offres dans MongoDB, et c'est le backend qui les vectorise
(au prochain POST /matches/refresh, ou avec `python -m app.modules.offers.indexing`).
"""
import hashlib
import logging
import uuid
from datetime import datetime, timezone
from pymongo.database import Database
from app.ai.embeddings import embed_documents, embed_query
from app.core.config import settings
from app.modules.cv.indexing import build_chunks
from app.vector import store

logger = logging.getLogger(__name__)

EMBED_BATCH = 32            # offres envoyées à Ollama par requête
DESCRIPTION_CHARS = 1500    # au-delà, le texte n'apporte plus grand-chose et ralentit l'embedding
_NAMESPACE = uuid.UUID("2b8e4f3a-6c1d-4e7f-9a20-5d3c8b1e7f42")


def is_active(offer: dict) -> bool:
    return offer.get("status", "ACTIVE") == "ACTIVE"


def offer_text(offer: dict) -> str:
    """Texte vectorisé : ce qui décrit le poste (le nom de l'entreprise n'aide pas à comparer des métiers)."""
    parts = [f"Offre : {offer.get('title') or ''}."]
    if offer.get("required_skills"):
        parts.append("Compétences : " + ", ".join(offer["required_skills"]) + ".")
    if offer.get("level"):
        parts.append(f"Niveau : {offer['level']}.")
    description = " ".join((offer.get("description") or "").split())
    if description:
        parts.append(description[:DESCRIPTION_CHARS])
    return " ".join(parts)


def fingerprint(offer: dict, model: str) -> str:
    return hashlib.sha256(f"{model}\n{offer_text(offer)}".encode()).hexdigest()


def point_id(offer_id) -> str:
    return str(uuid.uuid5(_NAMESPACE, str(offer_id)))


def index_offers(db: Database, cfg=settings, limit: int | None = None) -> dict:
    """Vectorise les offres actives nouvelles ou modifiées (au plus `limit`, les plus récentes d'abord),
    retire de Qdrant les offres expirées. Lève une exception si Ollama ou Qdrant est indisponible
    (les offres déjà traitées restent enregistrées)."""
    model = cfg.ollama_embed_model
    todo, stale, unchanged = [], [], 0
    for offer in db.offers.find({}, {"title": 1, "description": 1, "required_skills": 1, "level": 1,
                                     "source": 1, "source_id": 1, "status": 1, "vector_index": 1}):
        if not is_active(offer):
            if offer.get("vector_index"):
                stale.append(offer)
            continue
        fp = fingerprint(offer, model)
        if (offer.get("vector_index") or {}).get("fingerprint") == fp:
            unchanged += 1
        else:
            todo.append((offer, fp))

    remaining = 0
    if limit is not None and len(todo) > limit:
        todo.sort(key=lambda t: str(t[0]["_id"]), reverse=True)   # ObjectId croissant = plus récent en dernier
        remaining = len(todo) - limit
        todo = todo[:limit]

    client = store.get_client(cfg)
    if stale:
        store.delete_ids(client, store.OFFERS, [point_id(o["_id"]) for o in stale])
        db.offers.update_many({"_id": {"$in": [o["_id"] for o in stale]}}, {"$unset": {"vector_index": ""}})

    for start in range(0, len(todo), EMBED_BATCH):
        batch = todo[start:start + EMBED_BATCH]
        vectors = embed_documents([offer_text(o) for o, _ in batch], cfg)
        store.ensure_collection(client, store.OFFERS, len(vectors[0]), indexed_fields=("offer_id", "source"))
        now = datetime.now(timezone.utc)
        store.upsert(client, store.OFFERS, [
            (point_id(o["_id"]), vec, {"offer_id": str(o["_id"]), "source": o.get("source"),
                                       "source_id": o.get("source_id"), "title": o.get("title"),
                                       "embed_model": model, "indexed_at": now.isoformat()})
            for (o, _), vec in zip(batch, vectors)])
        for o, fp in batch:  # noté après l'écriture dans Qdrant : un échec en cours de route sera repris
            db.offers.update_one({"_id": o["_id"]}, {"$set": {"vector_index": {
                "fingerprint": fp, "model": model, "indexed_at": now}}})
    return {"vectorisees": len(todo), "inchangees": unchanged, "retirees": len(stale), "restantes": remaining}


def profile_query(profile: dict) -> str:
    """Le profil validé, en un seul texte (mêmes morceaux que ceux rangés dans cv_chunks)."""
    return " ".join(c["text"] for c in build_chunks(profile))


def similarities(profile: dict, cfg=settings) -> dict[str, float]:
    """Similarité cosinus entre le profil et chaque offre vectorisée : {offer_id: cosinus}."""
    query = profile_query(profile)
    if not query:
        return {}
    client = store.get_client(cfg)
    total = store.count_all(client, store.OFFERS)
    if not total:
        return {}
    hits = store.search(client, store.OFFERS, embed_query(query, cfg), limit=total)
    return {h.payload["offer_id"]: h.score for h in hits}


def semantic_scores(db: Database, profile: dict, cfg=settings, max_new: int | None = None,
                    stats_out: dict | None = None) -> dict[str, float] | None:
    """Vectorise les offres qui en ont besoin (au plus `max_new`) puis calcule les similarités. None si Ollama
    ou Qdrant est indisponible : le matching utilise alors le recouvrement de mots-clés. Une offre pas encore
    vectorisée est absente du résultat et passe, elle aussi, par les mots-clés."""
    try:
        stats = index_offers(db, cfg, limit=max_new)
        if stats_out is not None:
            stats_out.update(stats)
        if stats["vectorisees"] or stats["retirees"]:
            logger.info("Offres vectorisées : %s", stats)
        return similarities(profile, cfg)
    except Exception as e:  # Ollama éteint, modèle absent, dossier Qdrant verrouillé...
        logger.warning("Similarité sémantique indisponible, repli sur les mots-clés : %s", e)
        return None


def index_offers_quietly(db: Database, cfg=settings) -> None:
    """Pour une tâche de fond : vectorise tout ce qui reste, sans lever d'exception."""
    try:
        stats = index_offers(db, cfg)
        logger.info("Vectorisation en tâche de fond : %s", stats)
    except Exception as e:
        logger.warning("Vectorisation en tâche de fond interrompue : %s", e)


def main() -> int:
    """python -m app.modules.offers.indexing : vectorise les offres (par ex. juste après un run du scraper).
    En mode Qdrant local, le serveur uvicorn doit être arrêté (un seul processus à la fois)."""
    import json
    from app.db.mongo import get_db
    print(json.dumps(index_offers(get_db()), ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
