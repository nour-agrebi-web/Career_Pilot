# CareerPilot

Application mobile pilotée par un agent IA qui surveille les offres d'emploi, ne garde que celles adaptées au CV du candidat et l'aide à améliorer son CV.

Projet d'Intégration · ISET Radès · 2026/2027 · Mohamed Amine Hayouni, Nour Houda Agrebi, Ahmed Dhia eddine Battikh

## Structure

```text
backend/   API FastAPI (Python) + MongoDB, monolithe modulaire : auth, cv, profile, offers, notifications, admin
android/   Application Android native : Kotlin, MVVM, Jetpack Compose, Retrofit, Room (à ouvrir dans Android Studio)
.github/   Intégration continue : tests backend + tests et compilation de l'app Android
```

## Prérequis

- Python 3.12 ou 3.13 (les versions plus récentes n'ont pas encore toutes les dépendances précompilées sous Windows)
- MongoDB Community Server 8 (avec MongoDB Compass pour voir les données)
- Android Studio (avec un émulateur Android, API 26 ou plus)

## 1. Base de données MongoDB (une fois par machine)

1. Télécharger **MongoDB Community Server** (Windows, msi) sur mongodb.com/try/download/community.
2. Pendant l'installation : choisir **Complete**, laisser cocher **Install MongoD as a Service** et **Install MongoDB Compass**.
3. Vérifier : ouvrir Compass et se connecter à `mongodb://localhost:27017`.

Pas besoin de créer la base ni les collections : MongoDB les crée au premier enregistrement, et l'API crée ses index au démarrage.

## 2. Backend

```powershell
cd backend
py -3.13 -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env              # puis mettre un vrai JWT_SECRET
python -m seeds.seed_dev            # comptes de test (mot de passe : motdepasse123)
python -m app                       # API sur http://127.0.0.1:8000/docs (écoute sur 0.0.0.0)
pytest                              # lance les tests (base careerpilot_test)
```

Générer un `JWT_SECRET` : `python -c "import secrets; print(secrets.token_urlsafe(64))"`.

Créer un administrateur : s'inscrire normalement, puis dans Compass (ou mongosh) passer le champ `role` du document à `"admin"`. Le script de données crée déjà `admin@careerpilot.tn`.

## 3. Application Android

1. Lancer l'API avec `python -m app` (étape 2). Si Windows demande l'accès réseau pour Python, cliquer sur **Autoriser**.
2. Dans Android Studio : **File > Open** puis choisir le dossier `android/` (pas la racine du dépôt).
3. Attendre la fin de la synchronisation Gradle, puis lancer `app` sur un émulateur.

L'émulateur joint l'API du PC via `http://10.0.2.2:8000/` (réglé dans `android/app/src/main/java/tn/careerpilot/data/RetrofitInstance.kt`). Pour vérifier, ouvrir `http://10.0.2.2:8000/docs` dans le Chrome de l'émulateur.

Si l'app affiche « Impossible de joindre le serveur » :
- vérifier que `python -m app` tourne toujours ;
- sur un vrai téléphone, 10.0.2.2 n'existe pas : dans `RetrofitInstance.kt`, commenter la ligne de l'émulateur et décommenter `LOCAL_IP` (IPv4 du PC donnée par `ipconfig`) et le second `BASE_URL` ; PC et téléphone sur le même Wi-Fi ;
- en dernier recours (émulateur ou téléphone USB) : `adb reverse tcp:8000 tcp:8000` et `BASE_URL = "http://127.0.0.1:$API_PORT/"` dans `RetrofitInstance.kt`, à relancer après chaque redémarrage de l'appareil.

Après 3 échecs de connexion, l'e-mail est bloqué 3 min au 4e échec et au 5e, puis 15 min à partir du 6e ; l'app affiche le compte à rebours. Une connexion réussie remet le compteur à zéro. Pour débloquer un compte en dev, supprimer son document dans la collection `login_attempts`.

```text
android/app/src/main/java/tn/careerpilot/
  MainActivity.kt         point d'entrée
  CareerPilotApp.kt       création des objets partagés (API, stockage des jetons)
  data/                   Retrofit, jetons JWT chiffrés (Android Keystore), refresh automatique sur 401
  ui/auth/                écrans de connexion et d'inscription, AuthViewModel
  ui/home/                accueil (provisoire)
```

## API d'authentification

| Route | Rôle |
| --- | --- |
| `POST /auth/register` | Inscription (e-mail, mot de passe, langue, consentement obligatoire) |
| `POST /auth/login` | Connexion : jeton d'accès (15 min) + jeton de rafraîchissement (7 jours) |
| `POST /auth/refresh` | Nouvelle paire de jetons ; l'ancien refresh token est révoqué |
| `POST /auth/logout` | Révoque le refresh token |
| `GET /users/me` | Utilisateur connecté |
| `GET /admin/stats` | Réservé aux administrateurs |

## API métier (jeton d'accès obligatoire)

Chaque utilisateur n'accède qu'à ses propres données (US-1.3.1). Les erreurs renvoient `{"detail": "message en français"}`.

| Route | Rôle |
| --- | --- |
| `GET / PUT / DELETE /profile` | Lire, modifier (domaine, niveau) ou réinitialiser le profil |
| `POST /profile/validate` | Valider le profil (compétences + formation ou expérience obligatoires) |
| `GET / POST /profile/{section}` | Lister ou ajouter : `skills`, `experiences`, `educations`, `languages` |
| `PUT / DELETE /profile/{section}/{id}` | Modifier ou supprimer un élément du profil |
| `GET / PUT / DELETE /preferences` | Préférences : types d'offres, localisations, télétravail, seuil (50 à 90), notifications |
| `GET / POST /cvs` | Lister les versions ou importer un CV (PDF/DOCX, 5 Mo, 10 versions max, un seul actif) |
| `GET / DELETE /cvs/{id}`, `PUT /cvs/{id}/activate` | Consulter, supprimer ou réactiver une version |
| `GET /cvs/{id}/analysis` | Résultat de l'analyse : statut, profil déduit, sections vides, éléments à compléter |
| `POST /cvs/{id}/apply-profile` | Copier le profil déduit dans le profil (à relire puis valider) |
| `POST /cvs/{id}/analyze` | Relancer l'analyse (compte dans le quota IA) |
| `GET /cvs/quota` | Opérations IA utilisées aujourd'hui (5 par jour) |
| `GET /offers`, `GET /offers/{id}` | Offres actives (filtres `type` et `q`) |
| `POST /matches/refresh` | Lancer un cycle de matching (profil validé requis) |
| `GET /matches` | Offres recommandées triées par score (filtres `status`, `type`, `min_score` ; seuil des préférences par défaut) |
| `GET / PUT / DELETE /matches/{id}` | Détail, changement de statut (`ACCEPTED` = sauvegardée) ou « Pas pour moi » |

Le score (0 à 100) suit le cahier des charges : 40 similarité + 30 compétences clés + 15 niveau + 15 préférences, après les filtres éliminatoires. La similarité est pour l'instant un recouvrement de mots-clés (`app/modules/offers/matching.py`), à remplacer par les embeddings de l'Epic 4. `python -m seeds.seed_dev` ajoute 8 offres d'exemple.

## Import et analyse du CV (Epic 2)

1. `POST /cvs` vérifie le format (PDF ou DOCX) et la taille (5 Mo), puis enregistre le fichier **chiffré** (Fernet, clé `CV_ENCRYPTION_KEY`) dans `backend/storage/`. Le CV est créé avec le statut `UPLOADED`.
2. Une tâche de fond passe le CV à `PROCESSING` et extrait le texte : pdfplumber pour les PDF, python-docx pour les DOCX, et OCR Tesseract si le PDF est un scan.
3. Le nom, l'e-mail, le téléphone et l'adresse sont retirés du texte, puis le texte est structuré en profil (compétences, expériences, formations, langues, domaine, niveau) :
   - avec `ANTHROPIC_API_KEY` dans le `.env` : par Claude (sortie JSON structurée) ;
   - sans clé, ou si l'API ne répond pas : par l'analyse locale par règles (titres de sections et dates).
   Dans les deux cas, tout élément absent du texte du CV est retiré : l'agent n'invente rien.
4. Le CV passe à `PROCESSED` (profil déduit disponible) ou `ERROR` (`UNREADABLE` : fichier endommagé, `NO_TEXT` : scan illisible). L'application propose alors de réimporter un fichier ou de saisir le profil.
5. Le candidat appuie sur « Utiliser ce profil » : le profil est rempli, repasse à « non validé », et le candidat le relit puis le valide.

Quota : 5 opérations IA par jour et par utilisateur ; l'import d'un nouveau fichier en consomme une, un fichier déjà analysé est repris du cache sans en consommer.

**OCR des CV scannés (facultatif)** : installer Tesseract pour Windows (github.com/UB-Mannheim/tesseract/wiki) en cochant les langues French et Arabic, puis, s'il n'est pas dans le PATH, ajouter dans `.env` : `TESSERACT_CMD=C:\Program Files\Tesseract-OCR\tesseract.exe`. Sans Tesseract, un CV scanné passe en erreur `NO_TEXT` et la saisie manuelle est proposée.

## Architecture de l'application Android (MVVM)

```text
ui/            Vues Compose + ViewModels (état exposé en LiveData, appels dans viewModelScope)
  AppViewModelFactory   une factory qui injecte les repositories dans chaque ViewModel
  auth/ matches/ profile/ cv/ preferences/ stats/ main/
data/
  AuthRepository        connexion, inscription, session (jetons chiffrés + User dans Room)
  repository/           ProfileRepository, PreferencesRepository, CvRepository, MatchRepository
  remote/               Retrofit (CareerPilotApi, DTO, safeApiCall : try/catch + response.isSuccessful)
  local/                Room (AppDatabase, entités, DAO) : cache qui alimente les écrans
domain/        Modèles métier et règles testées sans Android (complétude du profil, filtres, statistiques, validations)
```

Les écrans observent le cache Room. Chaque action passe par l'API, puis le repository met à jour Room, et l'écran se rafraîchit tout seul.

## Workflow Git

- `main` reste toujours fonctionnelle.
- Une branche par story du backlog (`feature/auth-register`, `feature/cv-upload`...), puis une pull request relue par un autre membre.
- La CI doit être verte avant de fusionner.
