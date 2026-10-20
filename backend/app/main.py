from fastapi import FastAPI
from app.modules.admin.router import router as admin_router
from app.modules.auth.router import router as auth_router

app = FastAPI(title="CareerPilot API")
app.include_router(auth_router)
app.include_router(admin_router)

@app.get("/health")
def health():
    return {"status": "ok"}
