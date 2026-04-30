from __future__ import annotations

from pathlib import Path

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

from .errors import ImporterError


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        populate_by_name=True,
    )

    api_base_url: str = Field(default="http://localhost:18090/api", alias="API_BASE_URL")
    authorization_header: str = Field(default="", alias="AUTHORIZATION_HEADER")
    username: str = Field(default="", alias="USERNAME")
    password: str = Field(default="", alias="PASSWORD")
    data_dir: Path | None = Field(default=None, alias="DATA_DIR")
    category_code: str = Field(default="TEXTILE_DEFECT", alias="CATEGORY_CODE")
    category_name: str = Field(default="纺织瑕疵", alias="CATEGORY_NAME")
    tag_group_code: str = Field(default="DEFECT", alias="TAG_GROUP_CODE")
    tag_group_name: str = Field(default="瑕疵", alias="TAG_GROUP_NAME")
    dry_run: bool = Field(default=True, alias="DRY_RUN")
    batch_size: int = Field(default=50, alias="BATCH_SIZE")
    skip_completed: bool = Field(default=True, alias="SKIP_COMPLETED")
    retry_failed: bool = Field(default=False, alias="RETRY_FAILED")
    run_dir: Path = Field(default=Path(".import-runs"), alias="RUN_DIR")
    report_file: Path | None = Field(default=None, alias="REPORT_FILE")
    request_timeout_seconds: float = Field(default=120.0, alias="REQUEST_TIMEOUT_SECONDS")

    @field_validator("report_file", mode="before")
    @classmethod
    def blank_report_file(cls, value: object) -> object:
        if isinstance(value, str) and not value.strip():
            return None
        return value

    @field_validator("authorization_header", mode="before")
    @classmethod
    def blank_auth_value(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip()
        return value

    @property
    def normalized_api_base_url(self) -> str:
        return self.api_base_url.strip().rstrip("/")

    @property
    def resolved_data_dir(self) -> Path:
        if self.data_dir is None:
            raise ImporterError("请在 .env 中配置 DATA_DIR，指向包含 0 到 20 子目录的数据目录。")
        return self.data_dir.expanduser().resolve()

    @property
    def resolved_run_dir(self) -> Path:
        return self.run_dir.expanduser().resolve()

    @property
    def resolved_report_file(self) -> Path | None:
        if self.report_file is None:
            return None
        return self.report_file.expanduser().resolve()

    def validate_for_run(self) -> None:
        if not self.normalized_api_base_url:
            raise ImporterError("请在 .env 中配置 API_BASE_URL。")
        if not self.authorization_header and (not self.username.strip() or not self.password):
            raise ImporterError("请配置 USERNAME 和 PASSWORD，或高级用法 AUTHORIZATION_HEADER。")
        if self.batch_size <= 0:
            raise ImporterError("BATCH_SIZE 必须大于 0。")
        data_dir = self.resolved_data_dir
        if not data_dir.exists():
            raise ImporterError(f"DATA_DIR 不存在：{data_dir}")
        if not data_dir.is_dir():
            raise ImporterError(f"DATA_DIR 不是目录：{data_dir}")
