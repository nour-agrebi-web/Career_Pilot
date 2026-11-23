# CareerPilot : rapport sur le scraping des offres d'emploi

> Rédigé le 23 novembre 2026, mis à jour le même jour (passage de SQLite à MongoDB). Code : `careerpilot/scraper/` (dossier partagé du projet).
> Toutes les règles des sites citées ici (robots.txt, conditions d'utilisation, formats) ont été **lues le 23/11/2026**.
> **Important :** l'environnement où le code a été écrit n'a pas d'accès réseau aux sites d'emploi (le proxy refuse `keejob.com`, `tanitjobs.com`, etc. avec une erreur 403). Les formats ont donc été relevés en lisant les vrais fichiers via un outil de lecture web, et le scraper a été exécuté sur des **données simulées** au même format. Aucune collecte réelle n'a encore été lancée. La section 9 explique comment la lancer.

---

## 1. En bref

- Un seul programme Python, `jobscraper`, avec **un adaptateur par site**, une seule dépendance (**pymongo**, déjà utilisée par le backend), stockage dans **la base MongoDB du backend** (collection `offers` partagée), lancé par **cron** ou le Planificateur de tâches Windows.
- **2 sources retenues** pour le marché tunisien : **Keejob** (fichier officiel listant toutes les offres en une requête, plus une page Markdown par offre) et **Tanitjobs** (sitemap déclaré dans robots.txt, plus les données structurées schema.org des pages d'offres).
- **Écartées** : LinkedIn et Indeed (leurs conditions interdisent le scraping), France Travail (son API Offres n'est plus ouverte au public).
- Optimisations : 1 requête pour lister tout un site, requêtes conditionnelles (réponse 304 sans contenu), cache HTTP, déduplication par URL et par empreinte, pause de 2 s entre deux requêtes au même site, plafond de pages lues par exécution, respect automatique de robots.txt.
- **Score de 0 à 100** par mots-clés pondérés. **Le score de chaque offre est stocké, même sous le seuil** : le seuil est un simple filtre de lecture. Baisser le seuil affiche des offres déjà en base, sans requête réseau ni recalcul.
- **28 tests automatiques, tous verts** (MongoDB simulé avec mongomock, comme dans les tests du backend). Sur les données simulées, la 1re exécution envoie 11 requêtes. La 2e, sans changement côté sites, en envoie 4, toutes répondues par « 304 Non modifié », et ne recalcule aucun score.

---

## 2. Kotlin ou Python ?

La question « l'application finale est-elle en Kotlin ou en Python ? » est en fait déjà tranchée par le dépôt `Career_Pilot` : **les deux**, avec des rôles différents.

| Partie | Langage | Rôle |
| --- | --- | --- |
| `android/` | Kotlin (Jetpack Compose, MVVM) | L'application mobile utilisée par le candidat |
| `backend/` | Python 3.13 (FastAPI, MongoDB) | L'API, le CV, le matching |

La collecte des offres est un traitement côté serveur. Elle va donc naturellement dans le backend Python, jamais dans l'application Kotlin : un téléphone ne doit pas scraper des sites. Le scraper est écrit en Python pour cette raison.

Un point reste à noter : le cahier des charges parle de React Native pour le mobile, alors que le code est en Kotlin. Ce n'est pas lié au scraper.

---

## 3. Étape 1 : choisir les sources et vérifier leurs règles

Le public de CareerPilot est tunisien : les comptes de test sont en `.tn`, et la note `ia-locale-ollama-qdrant.md` cite déjà Tanitjobs, Keejob, Aneti et TunisieJobs. Pour chaque site, j'ai vérifié trois points : robots.txt, les conditions d'utilisation, et l'existence d'un accès prévu pour les programmes (API, flux RSS, sitemap).

| Source | Accès prévu pour les programmes | robots.txt | Conditions d'utilisation | Décision |
| --- | --- | --- | --- | --- |
| **Keejob** | Oui. Son fichier [`llms.txt`](https://www.keejob.com/llms.txt) annonce `llms-full.txt` (toutes les offres actives, 847 au 23/11), une version `.md` de chaque offre et un [flux RSS](https://www.keejob.com/rss/jobs/) | Interdit `/api/`, les comptes, `/postuler/`, `/print/`, `paginate_by=`. Autorise les offres et les fichiers ci-dessus | Pas de page de conditions trouvée. Le site publie lui-même ces formats pour les programmes | **Retenue, source principale** |
| **Tanitjobs** | Pas d'API ni de RSS. Un [sitemap](https://www.tanitjobs.com/sitemap_index.xml) déclaré dans robots.txt, et un balisage schema.org `JobPosting` sur chaque offre ([article Tanitjobs](https://www.tanitjobs.com/blog/1028/diffusion-offre-emploi-tanitjobs-google-jobs-alertes-email-reseaux-sociaux-tunisie)) | `Allow: /`. Interdit `/admin/`, `/resumes/`, les recherches (`searchID=`, `keywords`…). PetalBot interdit | [Conditions](https://www.tanitjobs.com/terms-of-use/) : contenu propriété de Tanitjobs, copie « pour votre utilisation personnelle et à des fins non commerciales », interdiction de surcharger le site. Rien d'explicite sur les robots | **Retenue, avec prudence** : usage non commercial (projet étudiant), faible fréquence, lien vers l'offre d'origine |
| France Travail | [API Offres d'emploi](https://www.data.gouv.fr/dataservices/api-offres-demploi) : « n'est actuellement plus exposée », accès restreint sur demande | – | – | **Écartée pour l'instant** (adaptateur facile à ajouter si l'accès est accordé) |
| LinkedIn, Indeed | – | – | Leurs conditions interdisent le scraping | **Écartées** |
| Aneti, TunisieJobs, OptionCarrière | – | Non vérifié | Non vérifié | **À étudier** avant tout ajout |

Ce que le scraper ne fait jamais : contourner un CAPTCHA ou une protection anti-robot, se connecter à un compte, appeler une page interdite par robots.txt, lire les CV ou les pages de candidature.

---

## 4. Étape 2 : l'architecture

```text
cron (toutes les 3 h)
   │
   ▼
python -m jobscraper run
   │
   ├── PoliteClient (http.py) ── robots.txt · pause 2 s par site · ETag / If-Modified-Since · reprises 429/5xx
   │        ▲
   │        └── cache HTTP (collection scraper_http_cache)
   │
   ├── Adaptateurs (sources/)
   │      keejob.py     : llms-full.txt (liste complète) → pages .md (détail) · secours : RSS
   │      tanitjobs.py  : sitemap.xml (liste + lastmod) → JSON-LD JobPosting (détail)
   │
   ├── normalize.py ── URL canonique, empreinte de contenu, empreinte de doublon, contrat, télétravail
   ├── storage.py   ── MongoDB (base du backend) : offers · offer_scores · scraper_http_cache · scraper_runs
   └── scoring.py   ── score 0-100 pour CHAQUE offre nouvelle ou modifiée → collection offer_scores

python -m jobscraper list --seuil N   ← lecture seule, aucune requête réseau
```

| Fichier | Rôle |
| --- | --- |
| `jobscraper/http.py` | Client HTTP poli : robots.txt, pause par hôte, cache conditionnel, reprises avec `Retry-After`, gzip |
| `jobscraper/sources/keejob.py` | Lecture de `llms-full.txt`, des pages `.md` et du RSS |
| `jobscraper/sources/tanitjobs.py` | Lecture du sitemap et du JSON-LD (avec secours sur les balises `<meta>`) |
| `jobscraper/normalize.py` | Normalisation du texte (accents), des URL, des types de contrat ; empreintes |
| `jobscraper/storage.py` | Collections et index MongoDB, conversion au format `offers` du backend |
| `jobscraper/scoring.py` | Calcul du score |
| `jobscraper/pipeline.py` | Enchaînement : lister, enregistrer, lire les détails utiles, scorer |
| `jobscraper/__main__.py` | Ligne de commande : `run`, `list`, `rescore`, `stats` |
| `profile.example.json` | Mots-clés pondérés du CV (exemple) |
| `tests/` | 28 tests et données simulées |

Les champs des offres reprennent ceux de `backend/seeds/offers_dev.py` : `source`, `source_id`, `title`, `company`, ville/pays, `type` (stage, emploi, freelance, temps_partiel), `work_mode`, `description`, `publication_date`, `url`. Le passage vers MongoDB sera donc direct.

---

## 5. Étape 3 : les requêtes utilisées

Toutes les requêtes sont des `GET` avec `User-Agent: CareerPilotBot/0.1 (projet etudiant; …)` et `Accept-Encoding: gzip`.

| # | Requête | Quand | Pourquoi c'est la moins coûteuse |
| --- | --- | --- | --- |
| 1 | `GET https://www.keejob.com/robots.txt` | 1 fois par exécution | Vérifier les règles avant toute autre requête |
| 2 | `GET https://www.keejob.com/llms-full.txt` + `If-None-Match` / `If-Modified-Since` | 1 fois par exécution | **Toutes** les offres actives (titre, entreprise, ville, contrat, mode, date) en **une seule** requête, au lieu de parcourir des dizaines de pages de résultats. Si rien n'a changé, le serveur répond 304 sans contenu |
| 3 | `GET https://www.keejob.com/offres-emploi/{id}/{slug}.md` | Seulement pour une offre **nouvelle** | Version Markdown prévue par Keejob : plus légère qu'une page HTML et plus simple à lire |
| 3b | `GET https://www.keejob.com/rss/jobs/` | Seulement si `llms-full.txt` est indisponible | Secours : 15 dernières offres, description incluse, donc aucune page détail à lire |
| 4 | `GET https://www.tanitjobs.com/robots.txt` | 1 fois par exécution | Idem |
| 5 | `GET https://www.tanitjobs.com/sitemap.xml` + en-têtes conditionnels | 1 fois par exécution | Liste de toutes les offres avec leur date de modification (`lastmod`) |
| 6 | `GET https://www.tanitjobs.com/job/{id}/{slug}/` | Seulement si l'offre est **nouvelle** ou si son `lastmod` **a changé** | On lit le bloc JSON-LD `JobPosting`, publié par le site pour Google Jobs, au lieu de dépendre de la mise en page HTML |

Jamais appelées : les pages de recherche (interdites par robots.txt chez Tanitjobs, et inutiles puisque la liste complète existe), `/api/` chez Keejob, les pages de candidature, d'impression et de compte.

### Les optimisations, une par une

| Technique | Où | Effet |
| --- | --- | --- |
| **Liste complète en 1 requête** | `llms-full.txt`, `sitemap.xml` | Chez Keejob : 1 requête pour 847 offres, au lieu de parcourir toutes les pages de résultats une par une |
| **Requêtes conditionnelles** (ETag, Last-Modified → 304) | `http.py` | Si la liste n'a pas changé, le serveur ne renvoie rien et le cache local est réutilisé |
| **Cache HTTP** en base | collection `scraper_http_cache` | Rien n'est retéléchargé inutilement, même après un redémarrage |
| **Déduplication par URL** | URL canonique (sans `?utm…`, sans `#`) + index unique `(source, source_id)` | Une offre déjà connue n'est jamais relue |
| **Détection de modification** | `lastmod` du sitemap, empreinte `content_hash` | Seules les offres modifiées sont relues et rescorées |
| **Doublons entre sites** | empreinte titre + entreprise + ville (`fingerprint`) | La même offre publiée sur Keejob et Tanitjobs n'apparaît qu'une fois |
| **Pause de 2 s par site** | `PoliteClient._wait_for_host` | Pas plus de 30 requêtes par minute vers un même site ; réglable avec `--delay` |
| **Reprise polie** | erreurs 429 et 5xx | Respecte `Retry-After` (plafonné à 5 min), sinon attente de 5, 10 puis 20 s ; au plus 3 essais |
| **Plafond par exécution** | `--max-details 40` | La 1re collecte est étalée sur plusieurs exécutions au lieu de lire 847 pages d'un coup. Les plus récentes passent en premier |
| **Offres retirées** | liste complète comparée à la base | Passées en `EXPIRED` (pas supprimées) et masquées dans `list` |
| **Requêtes indexées** | index `(profile_hash, score)` et `fingerprint` | Le filtre par seuil reste instantané même avec des milliers d'offres |

---

## 6. Étape 4 : le stockage MongoDB

> Mise à jour du 23/11 : le stockage SQLite de la 1re version a été remplacé par **MongoDB**, pour n'avoir **qu'une seule base** avec le backend. La logique reste la même : chaque score est stocké même sous le seuil, et les offres sont dédupliquées par URL et par empreinte.

Le scraper écrit dans **la même base que le backend** (`careerpilot` par défaut). Il lit les mêmes variables d'environnement `MONGODB_URL` et `MONGODB_DB`, ou les options `--mongo-url` et `--db`.

| Collection | Contenu | Index |
| --- | --- | --- |
| `offers` (**partagée avec le backend**) | Mêmes champs que `backend/seeds/offers_dev.py` : `source` (`Keejob`, `TanitJobs`), `source_id`, `title`, `company`, `location {city, country}`, `type` (stage, emploi, freelance, temps_partiel), `work_mode`, `description`, `publication_date` (date), `url`, `status` (`ACTIVE` ou `EXPIRED`), `required_skills`. S'y ajoutent les champs techniques du scraper : `content_hash`, `fingerprint`, `source_lastmod`, `detail_fetched`, `duplicate_of`, `expires_at`, `first_seen`, `last_seen` | `(source, source_id)` **unique**, le même que dans `backend/app/db/mongo.py` ; `url` ; `fingerprint` |
| `offer_scores` | Un score par offre **et par version du profil** : `offer_id`, `profile_hash`, `offer_hash`, `score`, `details` (compétences trouvées et manquantes, sous-scores, `partiel`), `scored_at` | `(offer_id, profile_hash)` unique ; `(profile_hash, score décroissant)` pour le filtre par seuil |
| `scraper_http_cache` | Dernier contenu reçu par URL, avec son `ETag` et son `Last-Modified` | `url` unique |
| `scraper_runs` | Statistiques de chaque exécution | – |

Les offres sont donc immédiatement visibles par l'API existante (`GET /offers`, `POST /matches/refresh`), sans conversion.

Choix à connaître :
- **Déduplication par URL :** l'index sur `url` n'est pas unique, parce que les 8 offres d'exemple du backend ont toutes la même URL d'accueil (`https://www.tanitjobs.com/`…). Un index unique ferait échouer leur insertion. La déduplication par URL est donc faite dans le code : avant toute insertion, le scraper cherche une offre avec la même URL normalisée.
- **Offres d'exemple protégées :** seules les offres qui ont un `content_hash` (celles écrites par le scraper) sont mises à jour, passées en `EXPIRED` ou scorées. Les offres de `seeds/offers_dev.py` ne sont jamais touchées. Un test le vérifie.
- **Pourquoi `offer_scores` et pas `matches` :** la collection `matches` du backend est liée à un utilisateur (`user_id`) et porte son suivi (vue, sauvegardée, masquée). `offer_scores` est liée à une version du profil. Le jour où le profil viendra de MongoDB, il suffira d'ajouter le `user_id` à ce document, ou d'écrire directement dans `matches`.
- **Offres retirées :** une offre absente de la liste complète du site passe en `status: EXPIRED`, la valeur que le backend utilise déjà pour écarter une offre du matching. Elle n'est jamais supprimée.

## 7. Étape 5 : le score et le seuil

### Le calcul (première version, par mots-clés)

| Critère | Points | Règle |
| --- | --- | --- |
| Compétences | 60 | Somme des poids (1 à 3) des compétences du CV trouvées dans l'offre, synonymes compris. Plein score à partir de 6 points (`saturation`) |
| Intitulé | 25 | Un métier visé figure dans le titre (25 points) ou seulement dans le texte (10 points) |
| Lieu | 10 | Ville souhaitée ou télétravail (10 points), lieu inconnu (5 points) |
| Contrat | 5 | Contrat souhaité (5 points), inconnu (2 points) |
| Exclusion | plafond 20 | Un mot exclu dans le titre (ex. « commercial ») plafonne le score à 20 |

Points techniques réglés :
- Les accents et les majuscules sont ignorés : « DÉVELOPPEUR » correspond à « developpeur ».
- Les pluriels sont reconnus : « conseiller client » correspond à « Conseillers Clients ».
- Les noms techniques fonctionnent : « C# » et « .NET » sont trouvés, et « C » ne correspond pas par erreur à « C# ».
- Si la page détail n'a pas encore été lue, le score est quand même calculé sur la ligne de liste et marqué `partiel`. Il est recalculé dès que le détail arrive.

Ce calcul est isolé dans `scoring.py`. Il pourra être remplacé par la similarité d'embeddings prévue (Ollama + Qdrant) sans toucher au reste.

### Les 3 cas demandés

| Cas | Ce qui se passe | Vérifié par le test |
| --- | --- | --- |
| **Offre au-dessus du seuil** | Le score est stocké, et l'offre apparaît dans `list` (et plus tard dans le fil de l'app) | `test_cas_au_dessus_et_sous_le_seuil` |
| **Offre sous le seuil** | Le score est **stocké quand même** ; l'offre est simplement masquée | `test_cas_au_dessus_et_sous_le_seuil`, `test_premiere_execution` (7 scores stockés dans `offer_scores` pour 7 offres) |
| **Changement de seuil** | `list --seuil 50` lit `offer_scores.find({score: {$gte: 50}})`. **Aucune requête réseau, aucun recalcul** : le test coupe le réseau et vérifie qu'il reste 0 offre à scorer. Le seuil est exclu de l'empreinte du profil : le changer ne déclenche donc jamais de recalcul | `test_changer_le_seuil_sans_reseau_ni_recalcul`, `test_seuil_hors_empreinte_profil` |

Autres cas gérés : un **profil modifié** (nouvelle compétence) déclenche un recalcul **hors ligne** de toutes les offres stockées. Une **offre modifiée** est la seule à être relue et rescorée.

---

## 8. Étape 6 : les tests

Lancement : `pip install -r requirements.txt` puis `python -m unittest discover -s tests -t .`. Résultat : **28 tests, 28 réussis** en environ 0,2 s. Le réseau est simulé par `tests/fakeweb.py`, qui gère les ETag et les 304, compte les requêtes et peut simuler des erreurs. MongoDB est simulé par **mongomock**.

| Groupe | Ce qui est vérifié |
| --- | --- |
| Lecture (7) | Ligne de `llms-full.txt`, page `.md` (section « Postuler » exclue), RSS, sitemap (pages hors offres ignorées), JSON-LD dans un `@graph`, secours sans JSON-LD, URL canonique |
| Score (4) | Accents, C#/.NET, pluriels, exclusion, bornes 0 et 100, seuil absent de l'empreinte du profil |
| HTTP (4) | Pause entre requêtes, refus d'une URL interdite par robots.txt (0 requête envoyée), reprise après 429 avec `Retry-After`, requête conditionnelle et 304 |
| Exécution complète et MongoDB (13) | 1re exécution (11 requêtes exactement, documents au format du backend), cas au-dessus et sous le seuil, changement de seuil hors ligne, 2e exécution (4 requêtes, toutes en 304, 0 recalcul), offre modifiée (1 seule page relue), doublon entre sites, plafond et reprise, offre retirée (`EXPIRED`), secours RSS, changement de profil, déduplication par URL normalisée, déduplication entre la liste et le RSS, offres d'exemple du backend intactes |

Pendant l'écriture, 3 tests ont échoué puis ont été corrigés : « C » trouvé à tort dans « C# » (correction du code), et deux valeurs attendues fausses dans les tests eux-mêmes.

---

## 9. Résultats d'exécution

### Sur les données simulées (exécuté)

Les données imitent le format réel (4 offres Keejob, 3 Tanitjobs dont une en double, avec un profil développeur Python/Kotlin à Tunis).

```text
$ python -m jobscraper run              → 11 requêtes (2 robots + 2 listes + 7 détails), 7 offres, 7 scores
$ python -m jobscraper list --seuil 70
3 offre(s) avec un score >= 70
100  Développeur Python / FastAPI (H/F)     | Nexa Digital | Tunis  | keejob
100  Stage PFE – Développeur Android Kotlin | Smart Hire   | Ariana | keejob
 90  Data Analyst junior                    | Orbit Labs   | Sfax   | keejob
$ python -m jobscraper list --seuil 40   → 4 offres (+ Tech Lead C# .NET, 55), sans aucune requête
$ python -m jobscraper list --seuil 0    → 6 offres (le doublon Tanitjobs de « Data Analyst junior » est masqué)
                                            dont Conseillers Clients 20 (exclusion) et Comptable 7
$ python -m jobscraper run              → 4 requêtes, toutes en 304, 0 page relue, 0 score recalculé
```

### Sur les vrais sites (pas encore exécuté)

Volume attendu, calculé à partir des fichiers lus le 23/11 : environ 847 offres actives chez Keejob et environ 288 offres dans le sitemap de Tanitjobs.

| Exécution | Requêtes estimées |
| --- | --- |
| 1re, avec `--max-details 40` | 4 + 40 + 40 = 84 requêtes, environ 3 minutes avec la pause de 2 s |
| Rattrapage initial | Environ 22 exécutions pour lire tous les détails Keejob (environ 4 jours à raison d'une exécution toutes les 3 h). Possible en une fois avec `--max-details 900` (environ 30 minutes) |
| Régime normal | 4 requêtes + 1 par offre nouvelle ou modifiée |

Pour lancer la vraie collecte sur un PC avec Python 3.10 ou plus récent, avec le MongoDB du backend démarré :

```bash
cd careerpilot/scraper
pip install -r requirements.txt
python -m unittest discover -s tests -t .
python -m jobscraper -v run --max-details 40
python -m jobscraper list --seuil 70
python -m jobscraper stats
```

---

## 10. Limites et points d'attention

1. **Aucune collecte réelle n'a été faite.** Les formats ont été relevés sur les vrais fichiers, mais pas le texte brut exact des pages. Deux points sont à confirmer à la 1re exécution réelle :
   - **Tanitjobs** : l'outil de lecture ne montre pas les balises `<script>`, et je n'ai donc pas pu voir directement le bloc JSON-LD, même si Tanitjobs annonce ce balisage sur chaque offre. Si le bloc manque, le secours ne récupère que le titre et le résumé `<meta>`. Il faudra alors un analyseur HTML dédié.
   - **Keejob** : la mise en forme exacte des métadonnées dans les pages `.md` (gras, deux-points) est déduite. L'analyseur accepte plusieurs variantes.
2. **Tanitjobs** autorise la copie pour un usage personnel et non commercial seulement. Le projet étudiant entre dans ce cadre. Une mise en production commerciale de CareerPilot demandera un accord écrit de Tanitjobs, ou de ne garder que le titre et le lien.
3. **Le sitemap de Tanitjobs** indiquait des `lastmod` du 5-6 octobre le 23 novembre : il est peut-être mis à jour rarement. Si c'est confirmé, les nouvelles offres y arriveront en retard, et il faudra chercher une autre liste autorisée.
4. **Le flux RSS de Keejob** ne contient que 15 offres. Il sert seulement de secours ; `llms-full.txt` est la vraie source.
5. **Le score par mots-clés** ne comprend pas le sens : « Python » dans « pas de Python requis » compte quand même. C'est voulu pour une première version, en attendant les embeddings.
6. **Le profil** est un fichier JSON écrit à la main. Il faudra le produire à partir du profil validé dans MongoDB.
7. Les ventilations du cahier des charges (40 similarité, 30 compétences, 15 niveau, 15 préférences) ne sont pas reprises telles quelles : c'est la version simplifiée validée. La formule complète existe déjà dans `backend/app/modules/offers/matching.py`.

---

## 11. Suite logique pour l'intégration dans le backend

1. Déplacer `jobscraper/` dans `backend/app/modules/offers/connectors/` et réutiliser `get_db()` du backend au lieu de créer un `MongoClient`. Le stockage est déjà en MongoDB, dans la même collection `offers`.
2. Produire le profil (`skills`, `titles`, `cities`, `contracts`) à partir du profil validé du candidat.
3. Remplacer cron par Celery Beat quand Redis sera en place (toutes les 6 h selon le cahier des charges).
4. Brancher l'endpoint `GET /matches?min_score=` de l'API sur la collection `offer_scores` (ou y écrire directement les `matches`) : le comportement « le seuil filtre sans recalcul » est déjà celui de ce scraper.
