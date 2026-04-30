from __future__ import annotations

import mimetypes
import sys
from pathlib import Path
from typing import Any, NoReturn
from urllib.parse import urljoin

import httpx

from .errors import ImporterError
from .settings import Settings


JsonObject = dict[str, Any]


class ApiClient:
    def __init__(self, settings: Settings, transport: httpx.BaseTransport | None = None) -> None:
        self.settings = settings
        self.base_url = settings.normalized_api_base_url + "/"
        timeout = httpx.Timeout(settings.request_timeout_seconds, connect=10.0)
        self.client = httpx.Client(timeout=timeout, transport=transport)
        self.refresh_token = ""
        self.manual_authorization = False

    def close(self) -> None:
        self.client.close()

    def __enter__(self) -> "ApiClient":
        return self

    def __exit__(self, *_: object) -> None:
        self.close()

    def authenticate(self) -> None:
        authorization_header = self.settings.authorization_header.strip()
        if authorization_header:
            self.manual_authorization = True
            self.client.headers.update({"Authorization": authorization_header})
            return

        legacy_token = self.settings.legacy_access_token.strip()
        if legacy_token:
            self.manual_authorization = True
            print("提示：ACCESS_TOKEN 已兼容读取，建议改用 AUTHORIZATION_HEADER 或 USERNAME/PASSWORD。", file=sys.stderr)
            self.client.headers.update({"Authorization": f"Bearer {legacy_token}"})
            return

        response = self._request(
            "POST",
            "auth/login",
            retry_on_unauthorized=False,
            json={
                "username": self.settings.username.strip(),
                "password": self.settings.password,
            },
        )
        payload = self._json_object(response)
        if payload.get("code") != "OK":
            raise ImporterError(f"登录失败：{payload.get('message') or '后端未返回成功状态'}")
        data = payload.get("data")
        if not isinstance(data, dict) or not data.get("accessToken"):
            raise ImporterError("登录失败：后端响应中没有 accessToken。")
        if not data.get("refreshToken"):
            raise ImporterError("登录失败：后端响应中没有 refreshToken。")
        self.refresh_token = str(data["refreshToken"])
        self.client.headers.update({"Authorization": f"Bearer {data['accessToken']}"})

    def categories(self) -> list[JsonObject]:
        return self._json_list(self._request("GET", "categories"))

    def tag_groups(self) -> list[JsonObject]:
        return self._json_list(self._request("GET", "tag-groups"))

    def tags(self, group_id: str) -> list[JsonObject]:
        return self._json_list(self._request("GET", "tags", params={"groupId": group_id}))

    def upload_deduplication_enabled(self) -> bool:
        payload = self._json_object(self._request("GET", "image-upload-settings"))
        return bool(payload.get("deduplicationEnabled", False))

    def create_upload_session(self, category_id: str, tag_ids: list[str], total_count: int) -> JsonObject:
        mode = "SINGLE" if total_count == 1 else "BATCH"
        return self._json_object(
            self._request(
                "POST",
                "image-upload-sessions",
                json={
                    "mode": mode,
                    "categoryId": category_id,
                    "tagIds": tag_ids,
                    "totalCount": total_count,
                },
            )
        )

    def stage_upload_item(self, session_id: str, file_path: Path, title: str) -> JsonObject:
        mime_type = mimetypes.guess_type(file_path.name)[0] or "application/octet-stream"

        def send_once() -> httpx.Response:
            url = urljoin(self.base_url, f"image-upload-sessions/{session_id}/items")
            with file_path.open("rb") as file:
                return self._send(
                    "POST",
                    url,
                    data={"title": title},
                    files={"file": (file_path.name, file, mime_type)},
                )

        try:
            response = send_once()
            if response.status_code == 401 and self.refresh_token:
                self.refresh_access_token()
                response = send_once()
        except OSError as exc:
            raise ImporterError(f"无法读取图片文件：{file_path}") from exc
        if response.is_success:
            return self._json_object(response)
        self._raise_response_error(response)

    def confirm_upload_session(self, session_id: str) -> JsonObject:
        return self._json_object(self._request("POST", f"image-upload-sessions/{session_id}/confirm"))

    def cancel_upload_session(self, session_id: str) -> JsonObject:
        return self._json_object(self._request("POST", f"image-upload-sessions/{session_id}/cancel"))

    def upload_session(self, session_id: str) -> JsonObject:
        return self._json_object(self._request("GET", f"image-upload-sessions/{session_id}"))

    def _request(self, method: str, path: str, retry_on_unauthorized: bool = True, **kwargs: Any) -> httpx.Response:
        url = urljoin(self.base_url, path.lstrip("/"))
        response = self._send(method, url, **kwargs)
        if response.status_code == 401 and retry_on_unauthorized and self.refresh_token:
            self.refresh_access_token()
            response = self._send(method, url, **kwargs)
        if response.is_success:
            return response

        self._raise_response_error(response)

    def refresh_access_token(self) -> None:
        response = self._request(
            "POST",
            "auth/refresh",
            retry_on_unauthorized=False,
            json={"refreshToken": self.refresh_token},
        )
        payload = self._json_object(response)
        if payload.get("code") != "OK":
            raise ImporterError(f"刷新登录状态失败：{payload.get('message') or '后端未返回成功状态'}")
        data = payload.get("data")
        if not isinstance(data, dict) or not data.get("accessToken"):
            raise ImporterError("刷新登录状态失败：后端响应中没有 accessToken。")
        if data.get("refreshToken"):
            self.refresh_token = str(data["refreshToken"])
        self.client.headers.update({"Authorization": f"Bearer {data['accessToken']}"})

    def _send(self, method: str, url: str, **kwargs: Any) -> httpx.Response:
        try:
            return self.client.request(method, url, **kwargs)
        except httpx.ConnectError as exc:
            raise ImporterError(f"无法连接后端 API：{self.settings.normalized_api_base_url}") from exc
        except httpx.TimeoutException as exc:
            raise ImporterError("请求后端 API 超时，请检查服务状态或调大 REQUEST_TIMEOUT_SECONDS。") from exc
        except httpx.RequestError as exc:
            raise ImporterError(f"请求后端 API 失败：{exc}") from exc

    def _raise_response_error(self, response: httpx.Response) -> NoReturn:
        detail = self._error_detail(response)
        if response.status_code == 401:
            if self.manual_authorization:
                raise ImporterError(f"认证失败：{detail}。手动 AUTHORIZATION_HEADER 可能已过期，建议改用 USERNAME/PASSWORD。")
            raise ImporterError(f"认证失败：{detail}")
        if response.status_code == 403:
            raise ImporterError(f"权限不足：{detail}")
        raise ImporterError(f"后端请求失败 HTTP {response.status_code}：{detail}")

    @staticmethod
    def _json_object(response: httpx.Response) -> JsonObject:
        payload = ApiClient._json(response)
        if not isinstance(payload, dict):
            raise ImporterError("后端响应格式不正确：预期 JSON 对象。")
        return payload

    @staticmethod
    def _json_list(response: httpx.Response) -> list[JsonObject]:
        payload = ApiClient._json(response)
        if not isinstance(payload, list) or any(not isinstance(item, dict) for item in payload):
            raise ImporterError("后端响应格式不正确：预期 JSON 数组。")
        return payload

    @staticmethod
    def _json(response: httpx.Response) -> Any:
        try:
            return response.json()
        except ValueError as exc:
            raise ImporterError("后端响应不是有效 JSON。") from exc

    @staticmethod
    def _error_detail(response: httpx.Response) -> str:
        try:
            payload = response.json()
        except ValueError:
            text = response.text.strip()
            return text[:500] if text else response.reason_phrase
        if isinstance(payload, dict):
            code = payload.get("code")
            message = payload.get("message")
            if code and message:
                return f"{code}: {message}"
            if message:
                return str(message)
        text = response.text.strip()
        return text[:500] if text else response.reason_phrase
