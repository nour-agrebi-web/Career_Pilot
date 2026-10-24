# CareerPilot

Application mobile pilotée par un agent IA qui surveille les offres d'emploi, ne garde que celles adaptées au CV du candidat et l'aide à améliorer son CV.

Projet d'Intégration · ISET Radès · 2026/2027 · Mohamed Amine Hayouni, Nour Houda Agrebi, Ahmed Dhia eddine Battikh

## Structure

```text
backend/   API FastAPI (Python) + MongoDB, monolithe modulaire : auth, cv, profile, offers, notifications, admin
android/   Application Android native : Kotlin, Jetpack Compose, Retrofit (à ouvrir dans Android Studio)
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

## Workflow Git

- `main` reste toujours fonctionnelle.
- Une branche par story du backlog (`feature/auth-register`, `feature/cv-upload`...), puis une pull request relue par un autre membre.
- La CI doit être verte avant de fusionner.
