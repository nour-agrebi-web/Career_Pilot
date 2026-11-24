# CareerPilot

[![CI](https://github.com/nour-agrebi-web/Career_Pilot/actions/workflows/ci.yml/badge.svg)](https://github.com/nour-agrebi-web/Career_Pilot/actions/workflows/ci.yml)

CareerPilot is a mobile application driven by an AI agent that monitors job offers, keeps only the ones that match the candidate's CV, and helps the candidate improve that CV. It is made of a FastAPI + MongoDB backend, a native Android app (Kotlin / Jetpack Compose) and a job-offer scraper.

Integration project · ISET Radès · 2026/2027.

## Main features

- **Authentication**: registration (with mandatory consent), login, JWT access token (15 min) + refresh token (7 days), token rotation on refresh, logout (refresh-token revocation).
- **Login lockout**: after 3 failed attempts the e-mail is locked for 1 min, then 3 min, then 5 min for each further lockout. A successful login resets the counter.
- **Candidate profile**: skills, experiences, education, languages, domain and level; profile validation and vectorisation into Qdrant.
- **Preferences**: offer types, locations, remote work, matching threshold (50–90), notifications.
- **CV import and analysis**: PDF/DOCX upload (5 MB max, 10 versions max, one active), encrypted storage (Fernet), text extraction (pdfplumber, python-docx, optional Tesseract OCR), anonymisation, then structuring into a profile by a **local** LLM (Ollama, `llama3.2:3b`) or by a rule-based fallback.
- **AI quota**: 5 AI operations per user per day; an already analysed file is served from cache.
- **Offer matching**: score from 0 to 100 (40 similarity + 30 key skills + 15 level + 15 preferences) after eliminatory filters; similarity uses embeddings (`nomic-embed-text` + Qdrant) or keyword overlap when Ollama/Qdrant is unavailable.
- **Recommended offers**: list sorted by score, detail, status change (`ACCEPTED` = saved) or "Not for me".
- **Scraper**: collects offers from Keejob and TanitJobs into the backend's MongoDB database, with categorisation and a relevance score.
- **Administration**: `GET /admin/stats`, restricted to administrators.

## Screenshots / demo

> To be completed: add screenshots of the Android app (login, profile, CV analysis, matches, statistics), for example in `./docs/screenshots/`, and reference them here.

```markdown
![Login screen](./docs/screenshots/login.png)
```

## Prerequisites

| Tool | Version | Used by |
| --- | --- | --- |
| Python | 3.12 or 3.13 (CI uses 3.13) | `backend/`, `scraper/` |
| MongoDB Community Server | 8 (CI uses 7.0) | `backend/`, `scraper/` |
| Android Studio + emulator | Android API 26 or higher (`minSdk = 26`, `targetSdk = 36`) | `android/` |
| JDK | 17 | `android/` (Gradle) |
| Ollama *(optional)* | — | local LLM + embeddings |
| Tesseract OCR *(optional)* | — | scanned CVs |

MongoDB Compass is recommended to inspect the data (`mongodb://localhost:27017`).

## Installation

```bash
git clone https://github.com/nour-agrebi-web/Career_Pilot.git
cd Career_Pilot
```

### Backend

Windows (PowerShell):

```powershell
cd backend
py -3.13 -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
```

Linux / macOS:

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```

Then edit `.env` and set a real `JWT_SECRET` (see below).

### Scraper

```bash
cd scraper
pip install -r requirements.txt
```

### Android app

1. In Android Studio: **File > Open** and select the `android/` folder (not the repository root).
2. Wait for the Gradle sync to finish.

### Local AI (optional)

Installing Ollama and the models on Windows, and checking the setup, is documented in [backend/docs/ia-locale.md](./backend/docs/ia-locale.md) (in French).

## Configuration / environment variables

The backend reads `backend/.env` (template: [backend/.env.example](./backend/.env.example)). The `.env` file must never be committed.

| Variable | Default in `.env.example` | Description |
| --- | --- | --- |
| `MONGODB_URL` | `mongodb://localhost:27017` | MongoDB connection URL |
| `MONGODB_DB` | `careerpilot` | Main database |
| `MONGODB_TEST_DB` | `careerpilot_test` | Database used by the tests |
| `JWT_SECRET` | placeholder | **Required.** Secret used to sign JWTs |
| `CV_ENCRYPTION_KEY` | *(empty)* | Fernet key used to encrypt stored CVs. Empty in dev: derived from `JWT_SECRET` |
| `AI_ENGINE` | `ollama` | `ollama` (local LLM) or `regles` (rule-based analysis only) |
| `OLLAMA_URL` | `http://localhost:11434` | Ollama server |
| `OLLAMA_MODEL` | `llama3.2:3b` | Model used to structure CVs |
| `OLLAMA_EMBED_MODEL` | `nomic-embed-text` | Embedding model |
| `QDRANT_PATH` | `storage/qdrant` | Local (embedded) Qdrant storage, used when `QDRANT_URL` is empty |
| `QDRANT_URL` | *(empty)* | Qdrant server URL (e.g. `http://localhost:6333`); when set, `QDRANT_PATH` is ignored |
| `TESSERACT_CMD` | *(commented out)* | Path to `tesseract.exe` on Windows if it is not in the `PATH` |

Generate secrets:

```bash
python -c "import secrets; print(secrets.token_urlsafe(64))"                                   # JWT_SECRET
python -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())"     # CV_ENCRYPTION_KEY
```

The scraper uses the same `MONGODB_URL` and `MONGODB_DB` variables (or the `--mongo-url` / `--db` options).

The Android app's API URL is set in `android/app/src/main/java/tn/careerpilot/data/RetrofitInstance.kt` (default for the emulator: `http://10.0.2.2:8000/`).

## Running the project

1. **MongoDB** must be running (installed as a service, or started manually). Databases, collections and indexes are created automatically.

2. **Backend** (from `backend/`, virtual environment activated):

   ```bash
   python -m seeds.seed_dev     # test accounts (password: motdepasse123) + 8 sample offers
   python -m app                # API on http://127.0.0.1:8000, listening on 0.0.0.0:8000
   ```

   Interactive API docs: <http://127.0.0.1:8000/docs>.

3. **Scraper** (from `scraper/`):

   ```bash
   python -m jobscraper run      # collect Keejob + TanitJobs, then score against profile.example.json
   python -m jobscraper watch    # continuous mode until Ctrl+C
   ```

4. **Android app**: run the `app` configuration on an emulator from Android Studio. To check connectivity, open `http://10.0.2.2:8000/docs` in the emulator's browser.

### Troubleshooting the Android connection

If the app shows "Impossible de joindre le serveur":

- check that `python -m app` is still running (on Windows, allow Python through the firewall when asked);
- on a real phone, `10.0.2.2` does not exist: in `RetrofitInstance.kt`, comment out the emulator line and uncomment `LOCAL_IP` (the PC's IPv4) and the second `BASE_URL`; the PC and the phone must be on the same Wi-Fi network;
- last resort (emulator or USB phone): `adb reverse tcp:8000 tcp:8000` and `BASE_URL = "http://127.0.0.1:$API_PORT/"` in `RetrofitInstance.kt` (to redo after each device restart).

## Usage

### Accounts

- `python -m seeds.seed_dev` creates test accounts, including the administrator `admin@careerpilot.tn` (password `motdepasse123`).
- To promote another user to administrator: register normally, then set the `role` field of the user document to `"admin"` (Compass or `mongosh`).
- To unlock a locked account in development, delete its document in the `login_attempts` collection.

### API

All business routes require an access token (`Authorization: Bearer <token>`). Each user can only access their own data. Errors are returned as `{"detail": "message"}` (messages are in French).

**Authentication**

| Method & route | Description |
| --- | --- |
| `POST /auth/register` | Register (e-mail, password, language, mandatory consent) |
| `POST /auth/login` | Login: access token + refresh token |
| `POST /auth/refresh` | New token pair; the previous refresh token is revoked |
| `POST /auth/logout` | Revoke the refresh token |
| `GET /users/me` | Current user |
| `GET /admin/stats` | Administrators only |

**Business API**

| Method & route | Description |
| --- | --- |
| `GET / PUT / DELETE /profile` | Read, update (domain, level) or reset the profile |
| `POST /profile/validate` | Validate the profile, then vectorise it into Qdrant |
| `GET /profile/vector-index` | Vectorisation status: `INDEXING`, `INDEXED` or `ERROR` |
| `GET / POST /profile/{section}` | List or add `skills`, `experiences`, `educations`, `languages` |
| `PUT / DELETE /profile/{section}/{id}` | Update or delete a profile item |
| `GET / PUT / DELETE /preferences` | Matching preferences |
| `GET / POST /cvs` | List CV versions or upload a CV (PDF/DOCX) |
| `GET / DELETE /cvs/{id}`, `PUT /cvs/{id}/activate` | Read, delete or reactivate a version |
| `GET /cvs/{id}/analysis` | Analysis result: status, inferred profile, empty sections |
| `POST /cvs/{id}/apply-profile` | Copy the inferred profile into the profile (to review then validate) |
| `POST /cvs/{id}/analyze` | Re-run the analysis (counts against the AI quota) |
| `GET /cvs/quota` | AI operations used today |
| `GET /offers`, `GET /offers/{id}` | Active offers (filters `type`, `q`, `category`) |
| `POST /matches/refresh` | Run a matching cycle (validated profile required) |
| `GET /matches` | Recommended offers sorted by score (filters `status`, `type`, `min_score`) |
| `GET / PUT / DELETE /matches/{id}` | Detail, status change, or "Not for me" |

**Example requests**

```bash
# Register (JSON body)
curl -X POST http://127.0.0.1:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "jane@example.com", "password": "secret1234", "language": "fr", "consent": true}'

# Login (OAuth2 form: the e-mail goes in the "username" field)
curl -X POST http://127.0.0.1:8000/auth/login \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin@careerpilot.tn&password=motdepasse123"

# Upload a CV
curl -X POST http://127.0.0.1:8000/cvs \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -F "file=@my_cv.pdf"

# Recommended offers above 70
curl "http://127.0.0.1:8000/matches?min_score=70" \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

> The exact request bodies are defined by the Pydantic schemas in `backend/app/modules/*/schemas.py` and are visible in the Swagger UI (`/docs`).

### CV analysis pipeline

1. `POST /cvs` checks the format and size, stores the file **encrypted** in `backend/storage/`, status `UPLOADED`.
2. A background task sets the status to `PROCESSING` and extracts the text (pdfplumber, python-docx, Tesseract OCR for scanned PDFs).
3. Personal data (name, e-mail, phone, address) is removed, then the text is structured into a profile by Ollama (JSON output constrained by a schema) or by the rule-based fallback. Anything not present in the CV text is discarded.
4. Status becomes `PROCESSED` or `ERROR` (`UNREADABLE` or `NO_TEXT`).
5. The candidate applies the inferred profile, reviews it, then validates it; the validated profile is split, embedded with `nomic-embed-text` and stored in Qdrant.

### Useful backend commands

```bash
python -m app.ai.check                                  # check the local AI setup
python -m app.modules.offers.indexing                   # vectorise new or updated offers
python -m app.modules.offers.diagnostic your@email      # find why offers in the DB do not appear in the app
```

### Useful scraper commands

```bash
python -m jobscraper list --seuil 70     # offers above the threshold (read-only, no network)
python -m jobscraper rescore             # recompute scores offline after a profile change
python -m jobscraper stats
python -m jobscraper categories          # categorise offers without a category
python -m jobscraper run --sources keejob --max-details 40 --delay 2 -v
```

See [scraper/README.md](./scraper/README.md) and [scraper/RAPPORT_SCRAPING.md](./scraper/RAPPORT_SCRAPING.md) (in French) for details and scheduling examples.

## Tests

Backend (requires a running MongoDB; uses the `careerpilot_test` database):

```bash
cd backend
pytest
```

Scraper (network and MongoDB are simulated with `mongomock`):

```bash
cd scraper
python -m unittest discover -s tests -t . -v
```

Android (unit tests + debug APK build):

```bash
cd android
./gradlew testDebugUnitTest assembleDebug
```

The same three jobs run in GitHub Actions on every push ([.github/workflows/ci.yml](./.github/workflows/ci.yml)).

## Project structure

```text
Career_Pilot/
├── .github/workflows/ci.yml   # CI: backend tests, scraper tests, Android tests + build
├── backend/                   # FastAPI API (Python) + MongoDB, modular monolith
│   ├── app/
│   │   ├── ai/                # Ollama client, embeddings, setup check
│   │   ├── core/              # config, security (JWT), encryption, validation
│   │   ├── db/                # MongoDB connection
│   │   ├── modules/           # auth, admin, profile, preferences, cv, offers, notifications
│   │   ├── vector/            # Qdrant store
│   │   └── main.py            # FastAPI app and routers
│   ├── docs/ia-locale.md      # local AI setup (Windows)
│   ├── seeds/                 # development data
│   ├── tests/                 # pytest suite
│   └── requirements.txt
├── android/                   # Native Android app (Kotlin, MVVM, Jetpack Compose, Retrofit, Room)
│   └── app/src/main/java/tn/careerpilot/
│       ├── data/              # Retrofit, encrypted JWT storage (Android Keystore), Room cache, repositories
│       ├── domain/            # business models and rules tested without Android
│       └── ui/                # Compose screens + ViewModels (auth, matches, profile, cv, preferences, stats)
└── scraper/                   # Keejob / TanitJobs collector → MongoDB `offers` collection
    ├── jobscraper/
    └── tests/
```

## Technologies

- **Backend**: Python, FastAPI, Uvicorn, PyMongo, Pydantic Settings, PyJWT, pwdlib (Argon2), cryptography (Fernet), pdfplumber, python-docx, pytesseract, qdrant-client, httpx, pytest.
- **AI (local)**: Ollama (`llama3.2:3b`, `nomic-embed-text`), Qdrant.
- **Database**: MongoDB.
- **Android**: Kotlin 2.2, Android Gradle Plugin 8.11, Jetpack Compose (BOM 2025.07.00), MVVM with LiveData, Retrofit, Room, Android Keystore.
- **Scraper**: Python, PyMongo, mongomock (tests).
- **CI**: GitHub Actions.

## Deployment

> To be completed: no production deployment configuration (Dockerfile, hosting, environment) is present in the repository. Document the target environment for the API, MongoDB, Ollama and Qdrant, and how the Android APK is distributed.

The API can currently be started with `python -m app` (Uvicorn with `reload=True`, intended for development).

## Contributing

- `main` must always stay functional.
- One branch per backlog story (e.g. `feature/auth-register`, `feature/cv-upload`), then a pull request reviewed by another team member.
- CI must be green before merging.

## License

> To be completed: no license file is present in the repository. Add a `LICENSE` file and mention it here.

## Authors / contacts

- Mohamed Amine Hayouni
- Nour El Houda Agrebi — [@nour-agrebi-web](https://github.com/nour-agrebi-web)

> To be completed: contact e-mails, supervisor(s).

## Useful resources

- [Backend local AI setup](./backend/docs/ia-locale.md)
- [Scraper documentation](./scraper/README.md)
- [Scraping report](./scraper/RAPPORT_SCRAPING.md)
- [FastAPI documentation](https://fastapi.tiangolo.com/)
- [Ollama](https://ollama.com/)
- [Qdrant documentation](https://qdrant.tech/documentation/)
- [Jetpack Compose](https://developer.android.com/compose)

## Notes to complete

- No `LICENSE` file.
- No screenshots of the Android app.
- No production deployment documentation or configuration.
- The `notifications` module and the `workers` package only contain an empty `__init__.py`; notification features described in preferences are not implemented on the backend yet.
- `backend/requirements.txt` lists `httpx2>=2.15.1` in addition to `httpx`: check that this package is intended.
- Linked documentation (`backend/docs/ia-locale.md`, `scraper/README.md`, `scraper/RAPPORT_SCRAPING.md`) is in French.
- Contact information for the authors is missing.
