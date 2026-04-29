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
