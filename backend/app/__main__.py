"""Lance l'API : `python -m app`.

Écoute sur toutes les interfaces (0.0.0.0), pour que l'émulateur (http://10.0.2.2:8000/)
comme un vrai téléphone sur le même Wi-Fi (http://<IP-du-PC>:8000/) puissent la joindre.
"""
import uvicorn

if __name__ == "__main__":
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)
