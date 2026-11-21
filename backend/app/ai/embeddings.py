"""Embeddings : transformer un texte en vecteur pour comparer des sens plutôt que des mots.

Le modèle nomic-embed-text attend un préfixe qui dit à quoi sert le texte :
- « search_document: » pour un texte que l'on stocke (morceaux du CV, et plus tard les offres) ;
- « search_query: » pour un texte avec lequel on cherche (ex. une offre qui cherche les CV proches).
Deux textes de sens proche donnent des vecteurs proches (similarité cosinus proche de 1).
"""
from app.ai.ollama import get_client

DOCUMENT_PREFIX = "search_document: "
QUERY_PREFIX = "search_query: "


def _prefixed(prefix: str, texts: list[str], model: str) -> list[str]:
    # Le préfixe n'a de sens que pour les modèles nomic ; les autres reçoivent le texte brut.
    return [prefix + t for t in texts] if "nomic" in model else list(texts)


def embed_documents(texts: list[str], settings) -> list[list[float]]:
    model = settings.ollama_embed_model
    return get_client(settings).embed(model, _prefixed(DOCUMENT_PREFIX, texts, model))


def embed_query(text: str, settings) -> list[float]:
    model = settings.ollama_embed_model
    return get_client(settings).embed(model, _prefixed(QUERY_PREFIX, [text], model))[0]
