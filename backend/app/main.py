import logging
from contextlib import asynccontextmanager
from fastapi import Depends, FastAPI, Request
from fastapi.responses import JSONResponse
from pymongo.database import Database
from pymongo.errors import ConnectionFailure
from app.db.mongo import create_indexes, get_db
from app.modules.admin.router import router as admin_router
from app.modules.auth.router import router as auth_router
from app.modules.cv.router import router as cv_router
from app.modules.offers.router import router as offers_router
from app.modules.preferences.router import router as preferences_router
from app.modules.profile.router import router as profile_router

logger = logging.getLogger(__name__)
DB_UNAVAILABLE = "Base de données MongoDB injoignable : vérifiez que le service MongoDB est démarré."

@asynccontextmanager
async def lifespan(app: FastAPI):
    try:
        create_indexes(get_db())
    except ConnectionFailure:
        # L'API démarre quand même : /health et les requêtes répondront 503 avec un message clair.
        logger.error(DB_UNAVAILABLE)
    yield

app = FastAPI(title="CareerPilot API", lifespan=lifespan)
app.include_router(auth_router)
app.include_router(admin_router)
app.include_router(profile_router)
app.include_router(preferences_router)
app.include_router(cv_router)
app.include_router(offers_router)

@app.exception_handler(ConnectionFailure)
async def mongo_unavailable(request: Request, exc: ConnectionFailure):
    logger.error("%s (%s)", DB_UNAVAILABLE, exc)
    return JSONResponse(status_code=503, content={"detail": DB_UNAVAILABLE})

@app.get("/health")
def health(db: Database = Depends(get_db)):
    db.command("ping")  # 503 si MongoDB est injoignable (voir mongo_unavailable)
    return {"status": "ok", "mongodb": "ok"}
