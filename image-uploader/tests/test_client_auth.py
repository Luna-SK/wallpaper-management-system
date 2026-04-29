import httpx
import pytest

from image_uploader.client import ApiClient
from image_uploader.errors import ImporterError
from image_uploader.settings import Settings


def settings(tmp_path, **kwargs) -> Settings:
    values = {
        "api_base_url": "http://example.test/api",
        "username": "admin",
        "password": "password",
        "data_dir": tmp_path,
    }
    values.update(kwargs)
    return Settings(**values)


def test_authorization_header_is_used_directly(tmp_path) -> None:
    seen_headers: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen_headers.append(request.headers.get("Authorization", ""))
        return httpx.Response(200, json=[])

    client = ApiClient(settings(tmp_path, authorization_header="Bearer manual-token"), transport=httpx.MockTransport(handler))
    try:
        client.authenticate()
        client.categories()
    finally:
        client.close()

    assert seen_headers == ["Bearer manual-token"]


def test_legacy_access_token_is_still_supported(tmp_path, capsys) -> None:
    seen_headers: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen_headers.append(request.headers.get("Authorization", ""))
        return httpx.Response(200, json=[])

    client = ApiClient(settings(tmp_path, legacy_access_token="legacy-token"), transport=httpx.MockTransport(handler))
    try:
        client.authenticate()
        client.categories()
    finally:
        client.close()

    assert seen_headers == ["Bearer legacy-token"]
    assert "ACCESS_TOKEN 已兼容读取" in capsys.readouterr().err


def test_username_password_login_sets_bearer_token(tmp_path) -> None:
    seen_headers: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/api/auth/login":
            return httpx.Response(
                200,
                json={"code": "OK", "data": {"accessToken": "access-1", "refreshToken": "refresh-1"}},
            )
        seen_headers.append(request.headers.get("Authorization", ""))
        return httpx.Response(200, json=[])

    client = ApiClient(settings(tmp_path), transport=httpx.MockTransport(handler))
    try:
        client.authenticate()
        client.categories()
    finally:
        client.close()

    assert seen_headers == ["Bearer access-1"]


def test_refresh_token_retries_once_after_unauthorized(tmp_path) -> None:
    categories_calls = 0
    refresh_calls = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal categories_calls, refresh_calls
        if request.url.path == "/api/auth/login":
            return httpx.Response(
                200,
                json={"code": "OK", "data": {"accessToken": "expired", "refreshToken": "refresh-1"}},
            )
        if request.url.path == "/api/auth/refresh":
            refresh_calls += 1
            assert request.headers.get("Authorization") == "Bearer expired"
            return httpx.Response(
                200,
                json={"code": "OK", "data": {"accessToken": "access-2", "refreshToken": "refresh-2"}},
            )
        if request.url.path == "/api/categories":
            categories_calls += 1
            if categories_calls == 1:
                assert request.headers.get("Authorization") == "Bearer expired"
                return httpx.Response(401, json={"code": "UNAUTHORIZED", "message": "expired"})
            assert request.headers.get("Authorization") == "Bearer access-2"
            return httpx.Response(200, json=[])
        raise AssertionError(f"unexpected path {request.url.path}")

    client = ApiClient(settings(tmp_path), transport=httpx.MockTransport(handler))
    try:
        client.authenticate()
        assert client.categories() == []
    finally:
        client.close()

    assert categories_calls == 2
    assert refresh_calls == 1


def test_manual_authorization_401_suggests_username_password(tmp_path) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(401, json={"code": "UNAUTHORIZED", "message": "expired"})

    client = ApiClient(settings(tmp_path, authorization_header="Bearer old"), transport=httpx.MockTransport(handler))
    try:
        client.authenticate()
        with pytest.raises(ImporterError, match="建议改用 USERNAME/PASSWORD"):
            client.categories()
    finally:
        client.close()


def test_file_upload_refresh_reopens_file_for_retry(tmp_path) -> None:
    upload_body_sizes: list[int] = []

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/api/auth/login":
            return httpx.Response(
                200,
                json={"code": "OK", "data": {"accessToken": "expired", "refreshToken": "refresh-1"}},
            )
        if request.url.path == "/api/auth/refresh":
            return httpx.Response(
                200,
                json={"code": "OK", "data": {"accessToken": "access-2", "refreshToken": "refresh-2"}},
            )
        if request.url.path == "/api/image-upload-sessions/session-1/items":
            upload_body_sizes.append(len(request.read()))
            if len(upload_body_sizes) == 1:
                return httpx.Response(401, json={"code": "UNAUTHORIZED", "message": "expired"})
            return httpx.Response(
                200,
                json={
                    "id": "session-1",
                    "items": [{"id": "item-1", "status": "STAGED", "candidateImageId": "candidate-1"}],
                },
            )
        raise AssertionError(f"unexpected path {request.url.path}")

    file_path = tmp_path / "a.jpg"
    file_path.write_bytes(b"image-bytes")
    client = ApiClient(settings(tmp_path), transport=httpx.MockTransport(handler))
    try:
        client.authenticate()
        response = client.stage_upload_item("session-1", file_path)
    finally:
        client.close()

    assert response["items"][0]["status"] == "STAGED"
    assert len(upload_body_sizes) == 2
    assert all(size > len(b"image-bytes") for size in upload_body_sizes)
