from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.db.mongo import create_indexes, get_db
from app.modules.admin.router import router as admin_router
from app.modules.auth.router import router as auth_router

@asynccontextmanager
async def lifespan(app: FastAPI):
    create_indexes(get_db())
    yield

app = FastAPI(title="CareerPilot API", lifespan=lifespan)
app.include_router(auth_router)
app.include_router(admin_router)

@app.get("/health")
def health():
    return {"status": "ok"}
