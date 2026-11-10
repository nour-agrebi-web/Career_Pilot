"""Offres d'exemple, en attendant les connecteurs de l'Epic 4 (TanitJobs, Stage.fr, France Travail...)."""
from datetime import datetime, timedelta, timezone


def _days_ago(n: int) -> datetime:
    return datetime.now(timezone.utc) - timedelta(days=n)


OFFERS = [
    dict(source="TanitJobs", source_id="tj-1001", title="Stage PFE – Développeur Android Kotlin",
         company="Nexa Digital", location={"city": "Tunis", "country": "Tunisie"}, type="stage",
         work_mode="hybride", level="stage", language="Français",
         description="Développement d'une application mobile native en Kotlin avec architecture MVVM, "
                     "Retrofit et Room. Tests unitaires et intégration continue.",
         required_skills=["Kotlin", "Android", "MVVM", "Retrofit", "Docker"], publication_date=_days_ago(1),
         url="https://www.tanitjobs.com/"),
    dict(source="France Travail", source_id="ft-2002", title="Développeur Python junior (CDI)",
         company="Atlas Software", location={"city": "Sousse", "country": "Tunisie"}, type="emploi",
         work_mode="sur_site", level="junior", language="Français",
         description="Conception d'API REST avec FastAPI et MongoDB, tâches asynchrones avec Celery.",
         required_skills=["Python", "FastAPI", "MongoDB", "Celery", "CI"], publication_date=_days_ago(2),
         url="https://www.francetravail.fr/"),
    dict(source="Stage.fr", source_id="sf-3003", title="Mission freelance – API FastAPI",
         company="DataBridge", location={"city": "Tunis", "country": "Tunisie"}, type="freelance",
         work_mode="teletravail", level="junior", language=None,
         description="Développement d'une API REST FastAPI pour un outil de reporting, en télétravail.",
         required_skills=["FastAPI", "REST", "Python"], publication_date=_days_ago(3),
         url="https://www.stage.fr/"),
    dict(source="TanitJobs", source_id="tj-1004", title="Assistant data – temps partiel",
         company="Orbit Labs", location={"city": "Tunis", "country": "Tunisie"}, type="temps_partiel",
         work_mode="hybride", level="stage", language="Français",
         description="Nettoyage de données, tableaux de bord et analyses en Python et SQL.",
         required_skills=["Python", "SQL", "Pandas", "Power BI"], publication_date=_days_ago(1),
         url="https://www.tanitjobs.com/"),
    dict(source="LinkedIn", source_id="li-5005", title="Stage d'été – Développeur React Native",
         company="Kairos Mobile", location={"city": "Tunis", "country": "Tunisie"}, type="stage",
         work_mode="sur_site", level="stage", language="Anglais",
         description="Participation au développement d'une application React Native et TypeScript.",
         required_skills=["React Native", "JavaScript", "TypeScript", "Git"], publication_date=_days_ago(4),
         url="https://www.linkedin.com/jobs/"),
    dict(source="France Travail", source_id="ft-2006", title="Ingénieur DevOps confirmé",
         company="CloudNord", location={"city": "Lyon", "country": "France"}, type="emploi",
         work_mode="sur_site", level="confirme", language="Français",
         description="Kubernetes, Terraform et pipelines CI/CD pour une plateforme SaaS.",
         required_skills=["Kubernetes", "Terraform", "Docker", "CI"], publication_date=_days_ago(6),
         url="https://www.francetravail.fr/"),
    dict(source="TanitJobs", source_id="tj-1007", title="Stage PFE – Intelligence artificielle et NLP",
         company="Smart Hire", location={"city": "Ariana", "country": "Tunisie"}, type="stage",
         work_mode="hybride", level="stage", language="Français",
         description="Analyse automatique de CV avec Python, NLP et modèles de langage.",
         required_skills=["Python", "NLP", "Machine Learning", "FastAPI"], publication_date=_days_ago(2),
         url="https://www.tanitjobs.com/"),
    dict(source="Stage.fr", source_id="sf-3008", title="Développeur mobile Flutter (freelance)",
         company="AppForge", location={"city": "Sfax", "country": "Tunisie"}, type="freelance",
         work_mode="teletravail", level="junior", language=None,
         description="Application Flutter et Firebase pour une startup de livraison.",
         required_skills=["Flutter", "Dart", "Firebase"], publication_date=_days_ago(5),
         url="https://www.stage.fr/"),
]


def seed_offers(db) -> int:
    added = 0
    for offer in OFFERS:
        result = db.offers.update_one(
            {"source": offer["source"], "source_id": offer["source_id"]},
            {"$setOnInsert": {**offer, "status": "ACTIVE"}},
            upsert=True,
        )
        added += 1 if result.upserted_id else 0
    return added
