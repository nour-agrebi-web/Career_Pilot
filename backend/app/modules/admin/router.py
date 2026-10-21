from fastapi import APIRouter, Depends
from pymongo.database import Database
from app.db.mongo import get_db
from app.modules.auth.deps import require_admin

router = APIRouter(prefix="/admin", tags=["admin"], dependencies=[Depends(require_admin)])

@router.get("/stats")
def stats(db: Database = Depends(get_db)):
    return {"users": db.users.count_documents({})}
