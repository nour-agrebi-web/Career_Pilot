"""Vectorisation du CV validé (étape préparatoire au matching sémantique, Epic 4).

Quand le candidat valide son profil, on découpe ce profil en morceaux (« chunks ») :
- un morceau « profil » (domaine visé, niveau, langues),
- un morceau « compétences » (toutes les compétences ensemble),
- un morceau par expérience, un morceau par formation.
Chaque morceau devient un vecteur (embedding nomic-embed-text, via Ollama) stocké dans Qdrant, avec
user_id et cv_id dans le payload.

Une seule fois par CV : si le profil validé n'a pas changé depuis la dernière vectorisation de ce CV
(même cv_id, même empreinte du contenu), on ne refait rien. S'il a changé (le candidat a corrigé une
compétence puis revalidé), on remplace les vecteurs. Les vecteurs d'un ancien CV de l'utilisateur sont
supprimés : seul le profil validé actuel sert au matching.

On part du profil validé plutôt que du texte brut : il est relu par le candidat, sans données
personnelles, et contient exactement ce que le matching doit comparer aux offres.
"""
import hashlib
import json
import logging
import uuid
from datetime import datetime, timezone
from bson import ObjectId
from pymongo.database import Database
from app.ai.embeddings import embed_documents
from app.core.config import settings
from app.vector import store

logger = logging.getLogger(__name__)

MANUAL = "manuel"  # profil saisi à la main, sans CV importé
_NAMESPACE = uuid.UUID("6f1d3c0e-8a52-4c2b-9a77-0c4a1f6c2e10")


def _period(item: dict) -> str:
    start, end = item.get("start_date"), item.get("end_date")
    if not start:
        return ""
    return f" ({start[:7]} – {end[:7] if end else 'en cours'})"


def build_chunks(profile: dict) -> list[dict]:
    """Découpe le profil en morceaux de texte : [{key, section, text, item_id}]."""
    chunks = []
    head = []
    if profile.get("domain"):
        head.append(f"Domaine visé : {profile['domain']}.")
    if profile.get("level"):
        head.append(f"Niveau : {profile['level']}.")
    langs = [f"{lang['name']} ({lang['level']})" if lang.get("level") else lang["name"]
             for lang in profile.get("languages", [])]
    if langs:
        head.append("Langues : " + ", ".join(langs) + ".")
    if head:
        chunks.append({"key": "profil", "section": "profil", "text": " ".join(head), "item_id": None})
    skills = [s["name"] for s in profile.get("skills", [])]
    if skills:
        chunks.append({"key": "competences", "section": "skills",
                       "text": "Compétences : " + ", ".join(skills) + ".", "item_id": None})
    for e in profile.get("experiences", []):
        company = f" chez {e['company']}" if e.get("company") else ""
        chunks.append({"key": f"experience:{e.get('id')}", "section": "experiences", "item_id": e.get("id"),
                       "text": f"Expérience : {e['position']}{company}{_period(e)}."})
    for e in profile.get("educations", []):
        institution = f", {e['institution']}" if e.get("institution") else ""
        chunks.append({"key": f"formation:{e.get('id')}", "section": "educations", "item_id": e.get("id"),
                       "text": f"Formation : {e['degree']}{institution}{_period(e)}."})
    return chunks


def fingerprint(chunks: list[dict], model: str) -> str:
    raw = json.dumps([model] + [c["text"] for c in chunks], ensure_ascii=False)
    return hashlib.sha256(raw.encode()).hexdigest()


def point_id(user_id: str, cv_id: str, key: str) -> str:
    # Identifiant stable : revectoriser le même CV remplace ses points au lieu de les dupliquer.
    return str(uuid.uuid5(_NAMESPACE, f"{user_id}:{cv_id}:{key}"))


def index_profile(db: Database, user_id: ObjectId) -> None:
    """Tâche de fond lancée après la validation du profil. N'échoue jamais bruyamment :
    si Ollama ne répond pas, l'erreur est notée dans profiles.vector_index et la prochaine validation réessaie."""
    profile = db.profiles.find_one({"user_id": user_id})
    if profile is None or not profile.get("validated"):
        return
    cv_id = str(profile["source_cv_id"]) if profile.get("source_cv_id") else MANUAL
    chunks = build_chunks(profile)
    model = settings.ollama_embed_model
    digest = fingerprint(chunks, model)
    previous = profile.get("vector_index") or {}
    if previous.get("status") == "INDEXED" and previous.get("cv_id") == cv_id and previous.get("fingerprint") == digest:
        return  # déjà vectorisé pour ce CV et ce contenu : rien à refaire
    db.profiles.update_one({"_id": profile["_id"]}, {"$set": {"vector_index.status": "INDEXING"}})
    try:
        vectors = embed_documents([c["text"] for c in chunks], settings)
        client = store.get_client(settings)
        uid = str(user_id)
        # Un utilisateur = un profil validé : on retire les vecteurs de ses anciens CV avant d'écrire.
        store.delete_where(client, store.CV_CHUNKS, user_id=uid)
        if vectors:
            store.ensure_collection(client, store.CV_CHUNKS, len(vectors[0]))
            now = datetime.now(timezone.utc).isoformat()
            store.upsert(client, store.CV_CHUNKS, [
                (point_id(uid, cv_id, c["key"]), vec,
                 {"user_id": uid, "cv_id": cv_id, "section": c["section"], "item_id": c["item_id"],
                  "text": c["text"], "embed_model": model, "indexed_at": now})
                for c, vec in zip(chunks, vectors)])
    except Exception as e:
        logger.warning("Vectorisation du profil de %s impossible : %s", user_id, e)
        db.profiles.update_one({"_id": profile["_id"]}, {"$set": {"vector_index": {
            "status": "ERROR", "cv_id": cv_id, "error": str(e)[:300], "updated_at": datetime.now(timezone.utc)}}})
        return
    db.profiles.update_one({"_id": profile["_id"]}, {"$set": {"vector_index": {
        "status": "INDEXED", "cv_id": cv_id, "fingerprint": digest, "chunks": len(chunks), "model": model,
        "updated_at": datetime.now(timezone.utc)}}})
