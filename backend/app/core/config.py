from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")
    mongodb_url: str = "mongodb://localhost:27017"
    mongodb_db: str = "careerpilot"
    mongodb_test_db: str = "careerpilot_test"
    jwt_secret: str
    jwt_algorithm: str = "HS256"
    access_token_minutes: int = 15
    refresh_token_days: int = 7

settings = Settings()
