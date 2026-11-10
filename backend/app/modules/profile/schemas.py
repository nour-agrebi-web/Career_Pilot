from datetime import date
from enum import Enum
from typing import Literal
from pydantic import BaseModel, Field, model_validator

# Collection MongoDB "profiles" (un document par utilisateur) :
# { _id, user_id (unique), domain, level, validated, validated_at, updated_at,
#   skills: [{id, name}], experiences: [{id, position, company, start_date, end_date}],
#   educations: [{id, degree, institution, start_date, end_date}], languages: [{id, name, level}] }

Level = Literal["stage", "junior", "confirme"]
LanguageLevel = Literal["A1", "A2", "B1", "B2", "C1", "C2", "natif"]


class Section(str, Enum):
    skills = "skills"
    experiences = "experiences"
    educations = "educations"
    languages = "languages"


class ProfileUpdate(BaseModel):
    domain: str | None = Field(default=None, max_length=80)
    level: Level | None = None


class SkillIn(BaseModel):
    name: str = Field(min_length=1, max_length=60)


class _Period(BaseModel):
    start_date: date
    end_date: date | None = None  # None = en cours

    @model_validator(mode="after")
    def check_period(self):
        if self.end_date is not None and self.end_date < self.start_date:
            raise ValueError("la date de fin doit suivre la date de début")
        return self


class ExperienceIn(_Period):
    position: str = Field(min_length=1, max_length=100)
    company: str = Field(min_length=1, max_length=100)


class EducationIn(_Period):
    degree: str = Field(min_length=1, max_length=120)
    institution: str = Field(min_length=1, max_length=120)


class LanguageIn(BaseModel):
    name: str = Field(min_length=1, max_length=40)
    level: LanguageLevel


SECTION_SCHEMAS: dict[Section, type[BaseModel]] = {
    Section.skills: SkillIn,
    Section.experiences: ExperienceIn,
    Section.educations: EducationIn,
    Section.languages: LanguageIn,
}

# Champ qui ne doit pas apparaître deux fois dans une section (comparaison sans casse).
UNIQUE_FIELD = {Section.skills: "name", Section.languages: "name"}
