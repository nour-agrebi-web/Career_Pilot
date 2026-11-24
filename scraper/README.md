# jobscraper : collecte d'offres d'emploi pour CareerPilot

Python 3.10+. Une seule dépendance, **pymongo**, déjà présente dans le backend. Les offres sont écrites dans la
**même base MongoDB que le backend** (`careerpilot`, collection `offers`) ; les scores dans `offer_scores`.

```bash
pip install -r requirements.txt          # pymongo (+ mongomock pour les tests)
# Adresse et base : variables MONGODB_URL et MONGODB_DB (comme le backend), ou --mongo-url / --db
```

```bash
python -m jobscraper run                 # collecte Keejob + Tanitjobs, puis score selon profile.example.json
python -m jobscraper list --seuil 70     # offres au-dessus du seuil (lecture seule, aucune requête réseau)
python -m jobscraper list --seuil 50     # baisser le seuil fait ressortir les offres déjà stockées
python -m jobscraper rescore             # après modification du profil : recalcul hors ligne
python -m jobscraper stats
python -m unittest discover -s tests -t . # tests (réseau et MongoDB simulés)
```

En continu (Windows ou Linux), jusqu'à Ctrl+C :

```powershell
cd scraper
python -m jobscraper watch                    # cycles de 40 détails/site, 1 min entre deux cycles tant qu'il
                                              # reste du retard, puis 30 min entre deux cycles
python -m jobscraper watch --pause 15 --max-details 0   # variante : tout lire à chaque cycle, cycle toutes les 15 min
```

Options utiles de `run` : `--sources keejob`, `--max-details 40` (pages détail lues au plus par site et par
exécution), `--delay 2` (secondes entre deux requêtes au même site), `-v` pour le journal.

Planification (toutes les 3 heures, de 7 h à 22 h) :

```cron
0 7-22/3 * * * cd /chemin/vers/scraper && python3 -m jobscraper run >> scraper.log 2>&1
```

Sous Windows : Planificateur de tâches, action `python -m jobscraper run`, dossier de départ = ce dossier.

Vectorisation : le scraper n'utilise ni Ollama ni Qdrant. Le backend vectorise les offres nouvelles ou modifiées
au prochain `POST /matches/refresh`, ou avec `python -m app.modules.offers.indexing` (dossier `backend`).

Le rapport complet est dans `RAPPORT_SCRAPING.md`.
