"""Champs dont le matching du backend a besoin et que les sites ne donnent pas tels quels.

- required_skills : compétences citées dans l'offre, repérées dans un dictionnaire de compétences courantes.
  Sans elles, le backend donne 15/30 à toutes les offres (cas « aucune compétence exigée ») et une offre
  idéale plafonne vers 76/100 : presque rien n'atteint le seuil par défaut de 70.
- level : stage | junior | confirme, déduit du titre et de l'expérience demandée (sinon 8/15 au lieu de 15).
"""
from __future__ import annotations

import re

from .normalize import fold

# Nom affiché -> variantes (repliées : minuscules, sans accents). L'ordre n'a pas d'importance.
SKILLS: dict[str, list[str]] = {
    "Python": ["python"], "Java": ["java"], "Kotlin": ["kotlin"], "JavaScript": ["javascript", "js"],
    "TypeScript": ["typescript"], "PHP": ["php"], "C#": ["c#"], ".NET": [".net", "dotnet", "asp.net"],
    "C++": ["c++"], "C": ["langage c"], "Go": ["golang"], "Ruby": ["ruby"], "Swift": ["swift"],
    "Dart": ["dart"], "R": ["langage r"], "Scala": ["scala"],
    "React": ["react", "reactjs", "react.js"], "React Native": ["react native"], "Angular": ["angular"],
    "Vue.js": ["vue.js", "vuejs"], "Node.js": ["node.js", "nodejs"], "Next.js": ["next.js", "nextjs"],
    "Django": ["django"], "Flask": ["flask"], "FastAPI": ["fastapi"], "Spring": ["spring", "spring boot"],
    "Laravel": ["laravel"], "Symfony": ["symfony"], "Flutter": ["flutter"], "Android": ["android"],
    "iOS": ["ios"], "HTML": ["html", "html5"], "CSS": ["css", "css3"], "Tailwind": ["tailwind"],
    "SQL": ["sql"], "MySQL": ["mysql"], "PostgreSQL": ["postgresql", "postgres"], "Oracle": ["oracle"],
    "SQL Server": ["sql server"], "MongoDB": ["mongodb"], "Redis": ["redis"], "Elasticsearch": ["elasticsearch"],
    "Firebase": ["firebase"], "Docker": ["docker"], "Kubernetes": ["kubernetes", "k8s"],
    "Terraform": ["terraform"], "Ansible": ["ansible"], "Jenkins": ["jenkins"], "CI/CD": ["ci/cd", "ci cd"],
    "Git": ["git", "github", "gitlab"], "Linux": ["linux"], "AWS": ["aws"], "Azure": ["azure"],
    "GCP": ["gcp", "google cloud"], "API REST": ["api rest", "rest api", "restful"], "GraphQL": ["graphql"],
    "Microservices": ["microservices", "micro-services"], "Machine Learning": ["machine learning"],
    "Deep Learning": ["deep learning"], "NLP": ["nlp"], "Data Science": ["data science"],
    "Pandas": ["pandas"], "TensorFlow": ["tensorflow"], "PyTorch": ["pytorch"], "Spark": ["spark"],
    "Power BI": ["power bi", "powerbi"], "Tableau": ["tableau software"], "Excel": ["excel"],
    "SAP": ["sap"], "Salesforce": ["salesforce"], "Odoo": ["odoo"], "Sage": ["sage"],
    "Scrum": ["scrum"], "Agile": ["agile"], "Jira": ["jira"], "UML": ["uml"], "Figma": ["figma"],
    "Selenium": ["selenium"], "Cybersécurité": ["cybersecurite", "securite informatique"],
    "Réseaux": ["reseaux informatiques", "cisco"], "Photoshop": ["photoshop"], "AutoCAD": ["autocad"],
    "Comptabilité": ["comptabilite"], "Marketing digital": ["marketing digital"], "SEO": ["seo"],
    "Anglais": ["anglais"], "Allemand": ["allemand"],
}


def _pattern(variant: str) -> re.Pattern:
    words = [re.escape(w) for w in variant.split(" ")]
    return re.compile(rf"(?<![a-z0-9]){'[ -]+'.join(words)}(?![a-z0-9#+])")


_COMPILED = [(name, [_pattern(v) for v in variants]) for name, variants in SKILLS.items()]


def extract_skills(*texts: str | None, limit: int = 15) -> list[str]:
    """Compétences du dictionnaire présentes dans le texte, dans l'ordre d'apparition."""
    text = fold(" ".join(t for t in texts if t))
    found = []
    for name, patterns in _COMPILED:
        positions = [m.start() for p in patterns if (m := p.search(text))]
        if positions:
            found.append((min(positions), name))
    return [name for _, name in sorted(found)][:limit]


_YEARS = re.compile(r"(\d+)\s*(?:a|-|à)?\s*(\d+)?\s*ans?")


def detect_level(title: str | None, description: str | None, contract: str | None) -> str | None:
    t, d = fold(title), fold(description)
    if contract == "stage" or re.search(r"(?<![a-z])(stage|stagiaire|pfe|internship|alternance)(?![a-z])", t):
        return "stage"
    if re.search(r"(?<![a-z])(senior|confirme|experimente|expert|lead|chef|manager|responsable|directeur|architecte)(?![a-z])", t):
        return "confirme"
    if re.search(r"(?<![a-z])(junior|debutant|jeune diplome)(?![a-z])", t):
        return "junior"
    exp = re.search(r"experience\s*:?\s*(?:de\s*|minimum\s*)?(\d+)", d)
    if exp:
        return "junior" if int(exp.group(1)) < 3 else "confirme"
    if re.search(r"(?<![a-z])(junior|debutant|jeune diplome)(?![a-z])", d):
        return "junior"
    return None
