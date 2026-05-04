import pytest

from image_uploader.errors import ImporterError
from image_uploader.main import ImportPlan, load_settings, parse_args, print_header
from image_uploader.models import STATUS_PLANNED, ImportRecord, ScannedImage
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


def test_help_can_be_rendered(capsys) -> None:
    with pytest.raises(SystemExit) as exc:
        parse_args(["--help"])

    assert exc.value.code == 0
    output = capsys.readouterr().out
    assert "指定配置文件路径" in output
    assert "本地 checkpoint" in output
    assert "真实上传文件" in output


def test_header_labels_backend_deduplication_and_checkpoint(capsys, tmp_path) -> None:
    image = ScannedImage(
        file_path=tmp_path / "0" / "a.jpg",
        relative_path="0/a.jpg",
        folder="0",
        tag_names=("无疵点",),
        desired_title="无疵点001",
        size_bytes=10,
        mtime_ns=100,
        sha256="sha",
    )
    record = ImportRecord.from_image(image, STATUS_PLANNED)
    settings = Settings(
        _env_file=None,
        data_dir=tmp_path,
        username="admin",
        password="password",
        skip_completed=True,
        retry_failed=False,
    )
    settings.set_env_file_label(".env.local")
    plan = ImportPlan([image], {image.relative_path: record}, [image])

    print_header(settings, tmp_path, tmp_path / "state.jsonl", tmp_path / "report.csv", plan, True)

    output = capsys.readouterr().out
    assert "配置文件: .env.local" in output
    assert "后端上传去重（系统设置）: 开启" in output
    assert "跳过已完成（本地 checkpoint）: 开启" in output
    assert "仅重试失败/中断项: 关闭" in output
