import pytest

from image_uploader.errors import ImporterError
from image_uploader.main import load_settings, parse_args
from image_uploader.settings import Settings


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


def test_skip_completed_cli_defaults_to_env_or_settings() -> None:
    args = parse_args([])

    assert args.skip_completed is None


def test_skip_completed_cli_can_enable_explicitly() -> None:
    args = parse_args(["--skip-completed"])

    assert args.skip_completed is True
