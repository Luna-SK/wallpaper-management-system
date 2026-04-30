import pytest

from image_uploader.errors import ImporterError
from image_uploader.settings import Settings


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
