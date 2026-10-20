# CareerPilot

Application mobile pilotée par un agent IA qui surveille les offres d'emploi, ne garde que celles adaptées au CV du candidat et l'aide à améliorer son CV.

Projet d'Intégration · ISET Radès · 2026/2027 · Mohamed Amine Hayouni, Nour Houda Agrebi, Ahmed Dhia eddine Battikh

## Structure

```text
backend/   API FastAPI (Python), monolithe modulaire : auth, cv, profile, offers, notifications, admin
android/   Application Android native : Kotlin, Jetpack Compose, Retrofit (à ouvrir dans Android Studio)
.github/   Intégration continue : tests backend + tests et compilation de l'app Android
```

## Prérequis

- Python 3.12 ou 3.13 (les versions plus récentes n'ont pas encore toutes les dépendances précompilées sous Windows)
- PostgreSQL 18
- Android Studio (avec un émulateur Android, API 26 ou plus)

## 1. Base de données (une fois par machine)

```powershell
psql -U postgres -h localhost
```

```sql
CREATE ROLE careerpilot WITH LOGIN PASSWORD 'ChangeMoi_2026!';
CREATE DATABASE careerpilot_db   OWNER careerpilot ENCODING 'UTF8';
CREATE DATABASE careerpilot_test OWNER careerpilot ENCODING 'UTF8';
```

## 2. Backend

```powershell
cd backend
python -m venv .venv
.venv\Scripts\Activate.ps1          # macOS/Linux : source .venv/bin/activate
pip install -r requirements.txt
copy .env.example .env              # puis mettre un vrai JWT_SECRET
alembic upgrade head                # crée les tables (ou : psql ... -f seeds/schema.sql)
psql -U careerpilot -h localhost -d careerpilot_db -f seeds/seed_dev.sql   # comptes de test
uvicorn app.main:app --reload       # API sur http://127.0.0.1:8000/docs
pytest                              # lance les tests (base careerpilot_test)
```

Générer un `JWT_SECRET` : `python -c "import secrets; print(secrets.token_urlsafe(64))"`.

Modifier un modèle SQLAlchemy, puis créer la migration :

```powershell
alembic revision --autogenerate -m "description du changement"
alembic upgrade head
```

Créer un administrateur : s'inscrire normalement, puis dans psql `UPDATE users SET role = 'ADMIN' WHERE email = '...';`

## 3. Application Android

1. Lancer l'API avec `uvicorn app.main:app --reload` (étape 2).
2. Dans Android Studio : **File > Open** puis choisir le dossier `android/` (pas la racine du dépôt).
3. Attendre la fin de la synchronisation Gradle, puis lancer `app` sur un émulateur.

L'émulateur joint l'API du PC via `http://10.0.2.2:8000/` (réglé dans `android/gradle.properties`). Sur un vrai téléphone, lancer l'API avec `uvicorn app.main:app --reload --host 0.0.0.0` et remplacer `careerpilot.apiUrl` par `http://<IP-du-PC>:8000/`.

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
