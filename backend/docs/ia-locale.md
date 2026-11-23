# IA locale de CareerPilot : Ollama + Qdrant (sans Docker, sans API payante)

## En bref

- L'analyse du CV n'utilise plus Claude. Elle utilise un **LLM local** qui tourne sur ton PC grâce à **Ollama** (modèle `llama3.2:3b` par défaut). Rien ne part sur Internet et c'est gratuit.
- Si Ollama n'est pas lancé, l'application ne plante pas : elle revient à l'**analyse par règles** qui existait déjà.
- Le **filtre anti-invention** est gardé : tout ce que le LLM propose et qui n'est pas écrit dans le CV est retiré.
- Quand le candidat **valide** son profil, le profil est découpé en morceaux, chaque morceau devient un **vecteur** (modèle `nomic-embed-text`), et les vecteurs sont rangés dans **Qdrant** avec `user_id` et `cv_id`. Une seule fois par CV.
- Qdrant fonctionne **sans Docker**, en mode « embarqué » : c'est une bibliothèque Python qui écrit dans le dossier `backend/storage/qdrant`. Plus tard, une seule ligne du `.env` (`QDRANT_URL`) suffira pour passer à un vrai serveur Qdrant.

## Les notions en deux minutes

| Notion | Explication simple | Dans CareerPilot |
| --- | --- | --- |
| **LLM** | Un modèle qui lit un texte et écrit une réponse. | Lit le CV et renvoie un JSON : compétences, expériences, formations, langues. |
| **Ollama** | Un petit serveur installé sur le PC qui fait tourner des LLM. Il écoute sur `http://localhost:11434`. | Le backend lui envoie des requêtes HTTP, comme à une API. |
| **Sortie structurée** | On donne au LLM un schéma JSON, et Ollama l'oblige à répondre avec ce format. | Le schéma `DRAFT_SCHEMA` de `structuring.py`. |
| **Embedding** | Un texte transformé en liste de nombres (768 ici). Deux textes de sens proche donnent des listes proches. | « Développeur Android » et « Mobile Kotlin developer » seront proches, même sans mot commun. |
| **Chunk (morceau)** | Une partie du CV vectorisée séparément. | Un morceau « profil », un « compétences », un par expérience, un par formation. |
| **Base vectorielle** | Une base qui range des vecteurs et retrouve les plus proches d'un vecteur donné. | Qdrant, collections `cv_chunks` (profils) et `offers` (offres). |

## Le parcours complet

```text
                  ┌──────────── import du CV (POST /cvs) ────────────┐
PDF / DOCX ──► extraction du texte ──► anonymisation ──► structuration ──► brouillon de profil
              (pdfplumber, docx,       (nom, e-mail,      │
               Tesseract)               téléphone…)       ├─ Ollama llama3.2:3b (JSON imposé)
                                                          └─ secours : règles locales
                                                          puis filtre anti-invention

brouillon ──► « Utiliser ce profil » ──► le candidat corrige ──► POST /profile/validate
                                                                       │ (tâche de fond)
                                                                       ▼
                                   découpage en morceaux ──► nomic-embed-text (Ollama)
                                                                       │
                                                                       ▼
                                       Qdrant, collection cv_chunks : 1 point par morceau
                                       payload = user_id, cv_id, section, texte, modèle
```

Pourquoi vectoriser le **profil validé** et pas le texte brut du CV ? Parce qu'il a été relu par le candidat, qu'il ne contient plus de données personnelles, et que c'est exactement ce que le matching devra comparer aux offres.

« Une seule fois par CV » : on garde une empreinte (hash) du contenu vectorisé. Si le candidat revalide sans rien changer, on ne refait rien. S'il a corrigé quelque chose, on remplace les vecteurs (sans doublons). Quand il change de CV, les vecteurs de l'ancien CV sont supprimés.

## Les fichiers

| Fichier | Rôle |
| --- | --- |
| `app/ai/ollama.py` | Client HTTP d'Ollama : `chat_json` (LLM + schéma JSON) et `embed` (embeddings). Messages d'erreur clairs (« Ollama injoignable », « lancez ollama pull … »). |
| `app/ai/embeddings.py` | `embed_documents` / `embed_query`, avec les préfixes `search_document:` / `search_query:` attendus par nomic-embed-text. |
| `app/ai/check.py` | Diagnostic : `python -m app.ai.check`. |
| `app/modules/cv/structuring.py` | `OllamaStructurer` remplace `ClaudeStructurer`. Règles en secours, filtre anti-invention inchangé. |
| `app/modules/cv/indexing.py` | Découpage du profil, vectorisation, écriture dans Qdrant, « une fois par CV ». |
| `app/vector/store.py` | Accès à Qdrant : mode local (`QDRANT_PATH`) ou serveur (`QDRANT_URL`). Collections `cv_chunks` et `offers`. |
| `app/modules/profile/router.py` | `POST /profile/validate` lance la vectorisation ; `GET /profile/vector-index` donne son état. |
| `app/core/config.py`, `.env.example` | Nouveaux réglages `AI_ENGINE`, `OLLAMA_*`, `QDRANT_*`. La clé Anthropic est supprimée. |
| `app/modules/offers/indexing.py` | Vectorisation des offres nouvelles ou modifiées (collection `offers`) et similarité profil ↔ offres pour le matching. |
| `app/modules/offers/matching.py` | Score sur 100 : les 40 points « similarité » viennent des embeddings, ou des mots-clés en repli. |
| `tests/test_indexing.py`, `tests/test_offer_indexing.py` | Tests sans Ollama (embeddings factices) avec Qdrant en mémoire. |

## Installation sur Windows

### 1. Installer Ollama

1. Télécharger **OllamaSetup.exe** sur https://ollama.com/download (Windows 10 ou 11).
2. L'installer. Ollama démarre tout seul et une icône de lama apparaît près de l'horloge. Il reste lancé en arrière-plan.
3. Vérifier dans un terminal (PowerShell) :

```powershell
ollama --version
```

### 2. Télécharger les deux modèles

```powershell
ollama pull nomic-embed-text   # embeddings, environ 270 Mo
ollama pull llama3.2:3b        # LLM, environ 2 Go
ollama list                    # les deux modèles doivent apparaître
```

Quel LLM choisir ?

| Modèle | Disque | RAM conseillée | Remarque |
| --- | --- | --- | --- |
| `llama3.2:3b` (par défaut) | ~2 Go | 8 Go | Rapide, suffisant pour extraire un CV. |
| `mistral:7b` | ~4,1 Go | 16 Go | Plus précis, mais nettement plus lent sur un PC sans carte graphique. |

Pour utiliser Mistral : `ollama pull mistral:7b`, puis `OLLAMA_MODEL=mistral:7b` dans `backend\.env`.

Petit essai à la main, pour voir le modèle répondre : `ollama run llama3.2:3b "Bonjour"` (taper `/bye` pour sortir).

### 3. Mettre à jour le backend

```powershell
cd backend
.venv\Scripts\activate
pip install -r requirements.txt   # installe qdrant-client ; anthropic n'est plus nécessaire
```

Ajouter dans `backend\.env` (les valeurs par défaut conviennent, ces lignes sont surtout là pour être visibles) :

```env
AI_ENGINE=ollama
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=llama3.2:3b
OLLAMA_EMBED_MODEL=nomic-embed-text
QDRANT_PATH=storage/qdrant
QDRANT_URL=
```

Tu peux supprimer la ligne `ANTHROPIC_API_KEY` si elle y est encore.

### 4. Vérifier que tout est prêt

Le serveur FastAPI doit être **arrêté** pendant ce test (voir la limite du mode local plus bas).

```powershell
python -m app.ai.check
```

Résultat attendu (les durées dépendent du PC) :

```text
Ollama : http://localhost:11434
  ✓ modèle llama3.2:3b
  ✓ modèle nomic-embed-text
  ✓ embedding : 768 dimensions en 0.4 s
  ✓ LLM (ollama) en 12.3 s : ['Kotlin', 'Python', 'Git']
Qdrant : storage/qdrant  ✓ collections []
```

### 5. Lancer et tester

```powershell
uvicorn app.main:app --reload
```

Puis, dans Swagger (http://localhost:8000/docs) ou dans l'application Android :
1. importer un CV : `GET /cvs/{id}/analysis` doit afficher `"engine": "ollama"` (ou `"regles"` si Ollama était éteint) ;
2. « Utiliser ce profil », puis valider le profil ;
3. `GET /profile/vector-index` doit afficher `"status": "INDEXED"` et le nombre de morceaux.

## Limites à connaître

- **Vitesse** : sur un PC sans carte graphique, `llama3.2:3b` met environ 10 à 40 secondes par CV. Le cahier des charges demande moins de 30 s : c'est atteignable avec le 3B, pas toujours avec Mistral 7B. Le délai maximal est réglé à 120 s (`OLLAMA_TIMEOUT_SECONDS`) ; au-delà, l'analyse par règles prend le relais.
- **Qualité** : un petit modèle se trompe plus qu'un grand. C'est pour ça qu'on garde le filtre anti-invention et que le candidat relit toujours le profil avant de le valider.
- **Mode local de Qdrant** : un seul programme à la fois peut ouvrir le dossier `storage/qdrant`. Ne lance pas deux serveurs uvicorn en même temps, ni `python -m app.ai.check` pendant que le serveur tourne (erreur « already accessed by another instance »).
- Si Ollama est éteint au moment de la validation, la validation réussit quand même ; `vector-index` affiche `ERROR` et il suffit de revalider une fois Ollama relancé.

## Passer plus tard à un serveur Qdrant

Quand tu auras Docker (ou un compte Qdrant Cloud gratuit) :

```powershell
docker run -p 6333:6333 -v qdrant_data:/qdrant/storage qdrant/qdrant
```

puis dans `.env` : `QDRANT_URL=http://localhost:6333` (et `QDRANT_API_KEY=...` pour Qdrant Cloud). Aucun code à changer. Il faudra seulement revalider les profils (ou relancer la vectorisation) pour remplir le nouveau serveur, car les données du dossier local n'y sont pas copiées.

## Vectorisation des offres et matching sémantique

Les offres collectées par le scraper (`scraper/`) arrivent dans MongoDB. Le scraper n'a pas besoin d'Ollama : c'est le backend qui les vectorise.

- **Quand ?** Au début de chaque `POST /matches/refresh`, ou à la main avec `python -m app.modules.offers.indexing` (serveur arrêté en mode Qdrant local).
- **Quoi ?** Pour chaque offre active : un texte (titre, compétences, niveau, description) devient un vecteur `nomic-embed-text`, rangé dans la collection Qdrant `offers` avec `offer_id`, `source` et `source_id` dans le payload. Un point par offre, sans doublon.
- **Seulement le nouveau.** L'empreinte du texte vectorisé et du modèle est gardée dans l'offre (`offers.vector_index`). Une offre inchangée n'est pas renvoyée à Ollama ; une offre dont le scraper a modifié le contenu est revectorisée ; une offre expirée est retirée de Qdrant.
- **Matching.** Le profil validé (les mêmes morceaux que `cv_chunks`) est vectorisé comme une requête (`search_query:`), puis `store.search` donne la similarité cosinus avec chaque offre. Elle remplace le recouvrement de mots-clés dans les 40 points « similarité » ; compétences (30), niveau (15) et préférences (15) ne changent pas. Conversion : cosinus ≤ 0,40 → 0 point, ≥ 0,75 → 40 points, linéaire entre les deux (`COSINE_LOW` / `COSINE_HIGH` dans `matching.py`, à ajuster sur de vraies offres).
- **Repli.** Si Ollama est éteint, le modèle absent ou le dossier Qdrant verrouillé, le matching retombe sur les mots-clés, sans erreur. La réponse de `POST /matches/refresh` indique `"semantic_engine": "embeddings"` ou `"mots-cles"`.

Tester en local :

```powershell
ollama pull nomic-embed-text
cd scraper
python -m jobscraper run              # collecte les offres dans MongoDB (sans Ollama)
cd ..\backend
python -m app.modules.offers.indexing # facultatif : vectorise tout de suite, ex. {"vectorisees": 42, ...}
uvicorn app.main:app --reload
```

Puis dans Swagger : valider le profil, puis `POST /matches/refresh` doit répondre `"semantic_engine": "embeddings"`.

## La suite (pas encore faite)

1. **Autres connecteurs d'offres** (Aneti, TunisieJobs) dans le scraper. Il faut vérifier pour chaque site ses conditions d'utilisation et son `robots.txt`, et préférer un flux RSS ou une API quand il en existe. **LinkedIn interdit le scraping** : on ne l'inclut pas.
2. **Calibrer la similarité** : regarder les cosinus obtenus sur de vraies offres et ajuster `COSINE_LOW` / `COSINE_HIGH`.
