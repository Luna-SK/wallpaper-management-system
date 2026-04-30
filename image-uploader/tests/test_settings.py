import pytest

from image_uploader.errors import ImporterError
from image_uploader.main import load_settings, parse_args
from image_uploader.settings import Settings


CONFIG_ENV_NAMES = [
    "API_BASE_URL",
    "AUTHORIZATION_HEADER",
    "USERNAME",
    "PASSWORD",
    "DATA_DIR",
    "CATEGORY_CODE",
    "CATEGORY_NAME",
    "TAG_GROUP_CODE",
    "TAG_GROUP_NAME",
    "DRY_RUN",
    "BATCH_SIZE",
    "SKIP_COMPLETED",
    "RETRY_FAILED",
    "RUN_DIR",
    "REPORT_FILE",
    "REQUEST_TIMEOUT_SECONDS",
]


def clear_config_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    for name in CONFIG_ENV_NAMES:
        monkeypatch.delenv(name, raising=False)


def test_skip_completed_defaults_to_true() -> None:
    settings = Settings(_env_file=None)

    assert settings.skip_completed is True


def test_blank_report_file_is_treated_as_default(monkeypatch, tmp_path) -> None:
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    monkeypatch.setenv("USERNAME", "admin")
    monkeypatch.setenv("PASSWORD", "password")
    monkeypatch.setenv("REPORT_FILE", "")

    settings = Settings()

    assert settings.report_file is None


def test_blank_authorization_header_is_treated_as_empty(monkeypatch, tmp_path) -> None:
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    monkeypatch.setenv("USERNAME", "admin")
    monkeypatch.setenv("PASSWORD", "password")
    monkeypatch.setenv("AUTHORIZATION_HEADER", "   ")

    settings = Settings()

    assert settings.authorization_header == ""


def test_access_token_no_longer_satisfies_authentication(monkeypatch, tmp_path) -> None:
    monkeypatch.setenv("ACCESS_TOKEN", "legacy-token")

    settings = Settings(data_dir=tmp_path, username="", password="", authorization_header="")

    with pytest.raises(ImporterError, match="USERNAME 和 PASSWORD"):
        settings.validate_for_run()


def test_no_skip_completed_cli_overrides_default(monkeypatch, tmp_path) -> None:
    monkeypatch.chdir(tmp_path)

    settings = load_settings(["--no-skip-completed"])

    assert settings.skip_completed is False


def test_default_env_file_is_loaded(monkeypatch, tmp_path) -> None:
    clear_config_environment(monkeypatch)
    data_dir = tmp_path / "data"
    data_dir.mkdir()
    (tmp_path / ".env").write_text(
        "\n".join(
            [
                "API_BASE_URL=http://default.example/api",
                "USERNAME=admin",
                "PASSWORD=password",
                f"DATA_DIR={data_dir}",
                "DRY_RUN=true",
            ]
        ),
        encoding="utf-8",
    )
    monkeypatch.chdir(tmp_path)

    settings = load_settings([])

    assert settings.normalized_api_base_url == "http://default.example/api"
    assert settings.username == "admin"
    assert settings.password == "password"
    assert settings.data_dir == data_dir
    assert settings.dry_run is True


def test_env_file_argument_loads_specified_file(monkeypatch, tmp_path) -> None:
    clear_config_environment(monkeypatch)
    data_dir = tmp_path / "data"
    data_dir.mkdir()
    env_file = tmp_path / "custom.env"
    env_file.write_text(
        "\n".join(
            [
                "API_BASE_URL=http://custom.example/api",
                "USERNAME=cloud-admin",
                "PASSWORD=cloud-password",
                f"DATA_DIR={data_dir}",
            ]
        ),
        encoding="utf-8",
    )
    monkeypatch.chdir(tmp_path)

    settings = load_settings(["--env-file", str(env_file)])

    assert settings.normalized_api_base_url == "http://custom.example/api"
    assert settings.username == "cloud-admin"
    assert settings.password == "cloud-password"
    assert settings.data_dir == data_dir
    assert settings.env_file_label == str(env_file)


def test_cli_arguments_override_env_file(monkeypatch, tmp_path) -> None:
    clear_config_environment(monkeypatch)
    data_dir = tmp_path / "data"
    data_dir.mkdir()
    env_file = tmp_path / "custom.env"
    env_file.write_text(
        "\n".join(
            [
                "USERNAME=admin",
                "PASSWORD=password",
                f"DATA_DIR={data_dir}",
                "DRY_RUN=true",
                "SKIP_COMPLETED=false",
            ]
        ),
        encoding="utf-8",
    )

    settings = load_settings(["--env-file", str(env_file), "--no-dry-run", "--skip-completed"])

    assert settings.dry_run is False
    assert settings.skip_completed is True


def test_missing_env_file_argument_reports_path(tmp_path) -> None:
    env_file = tmp_path / "missing.env"

    with pytest.raises(ImporterError, match="配置文件不存在"):
        load_settings(["--env-file", str(env_file)])


def test_skip_completed_cli_defaults_to_env_or_settings() -> None:
    args = parse_args([])

    assert args.skip_completed is None


def test_skip_completed_cli_can_enable_explicitly() -> None:
    args = parse_args(["--skip-completed"])

    assert args.skip_completed is True
