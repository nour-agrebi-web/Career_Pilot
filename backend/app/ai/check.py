"""Vérifie que l'IA locale est prête : python -m app.ai.check

1. Ollama répond-il ?  2. Les deux modèles sont-ils téléchargés ?
3. Un embedding fonctionne-t-il ?  4. Le LLM structure-t-il un petit CV ?  5. Qdrant s'ouvre-t-il ?
"""
import sys
import time
import httpx
from app.core.config import settings


def main() -> int:
    ok = True
    print(f"Ollama : {settings.ollama_url}")
    try:
        tags = httpx.get(f"{settings.ollama_url}/api/tags", timeout=5).json()
    except httpx.HTTPError:
        print("  ✗ Ollama ne répond pas. Lancez l'application Ollama (ou « ollama serve »).")
        return 1
    installed = {m["name"] for m in tags.get("models", [])}
    for model in (settings.ollama_model, settings.ollama_embed_model):
        found = model in installed or f"{model}:latest" in installed
        print(f"  {'✓' if found else '✗'} modèle {model}" + ("" if found else f"  ->  ollama pull {model}"))
        ok &= found
    if not ok:
        return 1

    from app.ai.embeddings import embed_documents
    started = time.monotonic()
    vector = embed_documents(["Compétences : Kotlin, Python."], settings)[0]
    print(f"  ✓ embedding : {len(vector)} dimensions en {time.monotonic() - started:.1f} s")

    from app.modules.cv.structuring import structure_text
    sample = ("Compétences\nKotlin, Python, Git\nExpérience\n02/2025 - 06/2025 Stagiaire Android chez Nexa\n"
              "Formation\n2023 - 2026 Licence DSI, ISET Rades")
    started = time.monotonic()
    draft, engine = structure_text(sample, settings)
    print(f"  {'✓' if engine == 'ollama' else '✗'} LLM ({engine}) en {time.monotonic() - started:.1f} s : "
          f"{[s['name'] for s in draft['skills']]}")
    ok &= engine == "ollama"

    from app.vector import store
    client = store.get_client(settings)
    print(f"Qdrant : {settings.qdrant_url or settings.qdrant_path}  ✓ collections {[c.name for c in client.get_collections().collections]}")
    store.reset_client()
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
