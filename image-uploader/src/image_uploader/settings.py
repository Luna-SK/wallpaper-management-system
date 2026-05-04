from __future__ import annotations

import os
from collections.abc import Mapping, Sequence
from pathlib import Path

from dotenv import dotenv_values
from pydantic import AliasChoices, Field, PrivateAttr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

from .errors import ImporterError


USERNAME_ENV = "IMAGE_UPLOADER_USERNAME"
PASSWORD_ENV = "IMAGE_UPLOADER_PASSWORD"
LEGACY_USERNAME_ENV = "USERNAME"
LEGACY_PASSWORD_ENV = "PASSWORD"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        populate_by_name=True,
    )

    api_base_url: str = Field(default="http://localhost:18090/api", alias="API_BASE_URL")
    authorization_header: str = Field(default="", alias="AUTHORIZATION_HEADER")
    username: str = Field(default="", validation_alias=AliasChoices(USERNAME_ENV, LEGACY_USERNAME_ENV))
    password: str = Field(default="", validation_alias=AliasChoices(PASSWORD_ENV, LEGACY_PASSWORD_ENV))
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
    _env_file_label: str = PrivateAttr(default=".env")

    def __init__(self, **data: object) -> None:
        env_file_values = _read_env_file_values(data.get("_env_file", self.model_config.get("env_file")))
        explicit_username = _has_explicit_config(data, "username", USERNAME_ENV, LEGACY_USERNAME_ENV)
        explicit_password = _has_explicit_config(data, "password", PASSWORD_ENV, LEGACY_PASSWORD_ENV)
        super().__init__(**data)
        if not explicit_username:
            self.username = _resolve_prefixed_env_value(
                env_file_values,
                USERNAME_ENV,
                LEGACY_USERNAME_ENV,
                self.username,
            )
        if not explicit_password:
            self.password = _resolve_prefixed_env_value(
                env_file_values,
                PASSWORD_ENV,
                LEGACY_PASSWORD_ENV,
                self.password,
            )

    def set_env_file_label(self, label: str) -> None:
        self._env_file_label = label

    @property
    def env_file_label(self) -> str:
        return self._env_file_label

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
            raise ImporterError(f"请在 {self.env_file_label} 中配置 DATA_DIR，指向包含 0 到 20 子目录的数据目录。")
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
            raise ImporterError(f"请在 {self.env_file_label} 中配置 API_BASE_URL。")
        if not self.authorization_header and (not self.username.strip() or not self.password):
            raise ImporterError(f"请配置 {USERNAME_ENV} 和 {PASSWORD_ENV}，或高级用法 AUTHORIZATION_HEADER。")
        if self.batch_size <= 0:
            raise ImporterError("BATCH_SIZE 必须大于 0。")
        data_dir = self.resolved_data_dir
        if not data_dir.exists():
            raise ImporterError(f"DATA_DIR 不存在：{data_dir}")
        if not data_dir.is_dir():
            raise ImporterError(f"DATA_DIR 不是目录：{data_dir}")


def _has_explicit_config(data: Mapping[str, object], field_name: str, *env_names: str) -> bool:
    return any(name in data for name in (field_name, *env_names))


def _read_env_file_values(env_file: object) -> dict[str, str | None]:
    values: dict[str, str | None] = {}
    for candidate in _env_file_candidates(env_file):
        path = candidate.expanduser()
        if path.is_file():
            values.update(dotenv_values(path))
    return values


def _env_file_candidates(env_file: object) -> list[Path]:
    if env_file is None:
        return []
    if isinstance(env_file, str | Path):
        return [Path(env_file)]
    if isinstance(env_file, Sequence):
        return [Path(item) for item in env_file if item is not None]
    return [Path(env_file)]


def _resolve_prefixed_env_value(
    env_file_values: Mapping[str, str | None],
    preferred_env_name: str,
    legacy_env_name: str,
    fallback: str,
) -> str:
    for source, name in (
        (os.environ, preferred_env_name),
        (env_file_values, preferred_env_name),
        (env_file_values, legacy_env_name),
        (os.environ, legacy_env_name),
    ):
        if name in source:
            value = source[name]
            return "" if value is None else str(value)
    return fallback
