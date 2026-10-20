from fastapi import APIRouter, Depends
from sqlalchemy import func, select
from sqlalchemy.orm import Session
from app.db.session import get_db
from app.modules.auth.deps import require_admin
from app.modules.auth.models import User

router = APIRouter(prefix="/admin", tags=["admin"], dependencies=[Depends(require_admin)])

@router.get("/stats")
def stats(db: Session = Depends(get_db)):
    return {"users": db.scalar(select(func.count()).select_from(User))}
