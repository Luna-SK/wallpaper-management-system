from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    api_base_url: str = Field(default="http://localhost:8080/api", alias="API_BASE_URL")
    access_token: str = Field(default="", alias="ACCESS_TOKEN")
    data_dir: Path = Field(alias="DATA_DIR")
    default_category_code: str = Field(default="TEXTILE_DEFECT", alias="DEFAULT_CATEGORY_CODE")
    default_category_name: str = Field(default="纺织瑕疵", alias="DEFAULT_CATEGORY_NAME")
    dry_run: bool = Field(default=True, alias="DRY_RUN")


settings = Settings()
