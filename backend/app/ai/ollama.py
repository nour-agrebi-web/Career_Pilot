"""Client minimal pour Ollama, le serveur d'IA locale (https://ollama.com).

Ollama tourne sur le PC (http://localhost:11434) et expose une petite API HTTP :
- POST /api/chat  : on envoie des messages à un LLM (ex. llama3.2:3b) et on reçoit sa réponse.
  Avec `format` = un schéma JSON, Ollama force le modèle à répondre avec un JSON conforme.
- POST /api/embed : on envoie des textes, on reçoit leurs embeddings (vecteurs de nombres).

Aucune clé, aucun coût : tout reste sur la machine.
"""
import json
import httpx


class OllamaError(RuntimeError):
    """Ollama injoignable (pas lancé), modèle absent (pas téléchargé) ou réponse invalide."""


class OllamaClient:
    def __init__(self, base_url: str, timeout: float):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    def _post(self, path: str, payload: dict) -> dict:
        try:
            response = httpx.post(f"{self.base_url}{path}", json=payload, timeout=self.timeout)
        except httpx.HTTPError as e:
            raise OllamaError(f"Ollama injoignable sur {self.base_url} ({e}). Est-il lancé ?") from e
        if response.status_code == 404:
            # Ollama renvoie 404 quand le modèle n'a pas été téléchargé (ollama pull <modèle>).
            raise OllamaError(f"Modèle introuvable : lancez « ollama pull {payload.get('model')} ».")
        if response.status_code >= 400:
            raise OllamaError(f"Erreur Ollama {response.status_code} : {response.text[:200]}")
        return response.json()

    def chat_json(self, model: str, system: str, user: str, schema: dict) -> dict:
        """Demande au LLM une réponse JSON qui respecte `schema` (sorties structurées d'Ollama)."""
        data = self._post("/api/chat", {
            "model": model,
            "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
            "format": schema,
            "stream": False,
            # temperature 0 : réponses stables, le modèle « invente » moins.
            # num_ctx : taille de la fenêtre de contexte (un CV + les consignes tiennent dans 8192 tokens).
            "options": {"temperature": 0, "num_ctx": 8192},
        })
        try:
            return json.loads(data["message"]["content"])
        except (KeyError, TypeError, json.JSONDecodeError) as e:
            raise OllamaError("Réponse du modèle illisible (JSON invalide).") from e

    def embed(self, model: str, texts: list[str]) -> list[list[float]]:
        """Un vecteur par texte, dans le même ordre."""
        if not texts:
            return []
        data = self._post("/api/embed", {"model": model, "input": texts})
        vectors = data.get("embeddings")
        if not vectors or len(vectors) != len(texts):
            raise OllamaError("Réponse d'embedding inattendue.")
        return vectors


def get_client(settings) -> OllamaClient:
    return OllamaClient(settings.ollama_url, settings.ollama_timeout_seconds)
