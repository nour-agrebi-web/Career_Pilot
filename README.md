# CareerPilot

Application mobile pilotée par un agent IA qui surveille les offres d'emploi, ne garde que celles adaptées au CV du candidat et l'aide à améliorer son CV.

Projet d'Intégration · ISET Radès · 2026/2027 · Mohamed Amine Hayouni, Nour Houda Agrebi, Ahmed Dhia eddine Battikh

## Structure

```text
backend/   API FastAPI (Python), monolithe modulaire : auth, cv, profile, offers, notifications, admin
mobile/    Application React Native (Expo, TypeScript)
.github/   Intégration continue : tests backend + vérification TypeScript du mobile
```

## Prérequis

- Python 3.12
- Node.js 22
- PostgreSQL 18
- Android Studio (émulateur) ou l'application Expo Go sur un téléphone

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
alembic upgrade head                # crée les tables
fastapi dev app/main.py             # API sur http://127.0.0.1:8000/docs
pytest                              # lance les tests (base careerpilot_test)
```

Générer un `JWT_SECRET` : `python -c "import secrets; print(secrets.token_urlsafe(64))"`.

Modifier un modèle SQLAlchemy, puis créer la migration :

```powershell
alembic revision --autogenerate -m "description du changement"
alembic upgrade head
```

Créer un administrateur : s'inscrire normalement, puis dans psql `UPDATE users SET role = 'ADMIN' WHERE email = '...';`

## 3. Mobile

```powershell
cd mobile
npm install
copy .env.example .env              # adresse de l'API
npx expo start                      # puis "a" pour l'émulateur Android
```

Sur un vrai téléphone, lancer l'API avec `fastapi dev app/main.py --host 0.0.0.0` et mettre `EXPO_PUBLIC_API_URL=http://<IP-du-PC>:8000` dans `mobile/.env`.

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
