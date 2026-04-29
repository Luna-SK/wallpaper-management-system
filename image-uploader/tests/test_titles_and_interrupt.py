import json
from pathlib import Path

import httpx
import pytest

from image_uploader.client import ApiClient
from image_uploader.main import (
    GracefulInterrupt,
    ImportPlan,
    collect_images,
    friendly_title,
    update_title_for_record,
    update_titles_for_records,
    upload_chunk,
)
from image_uploader.models import (
    STATUS_INTERRUPTED,
    STATUS_SKIPPED_COMPLETED,
    STATUS_UPLOADED,
    ImportRecord,
    ScannedImage,
)
from image_uploader.settings import Settings
from image_uploader.state import StateStore


def settings(tmp_path, **kwargs) -> Settings:
    values = {
        "api_base_url": "http://example.test/api",
        "authorization_header": "Bearer token",
        "data_dir": tmp_path,
    }
    values.update(kwargs)
    return Settings(**values)


def image(relative_path: str, desired_title: str = "破洞001") -> ScannedImage:
    folder = relative_path.split("/", 1)[0]
    return ScannedImage(
        file_path=Path("/data") / relative_path,
        relative_path=relative_path,
        folder=folder,
        tag_names=("破洞",),
        desired_title=desired_title,
        size_bytes=10,
        mtime_ns=100,
        sha256="sha",
    )


def test_friendly_title_uses_tags_and_three_digit_minimum() -> None:
    assert friendly_title(("破洞",), 1) == "破洞001"
    assert friendly_title(("水渍", "油渍", "污渍"), 2) == "水渍-油渍-污渍002"
    assert friendly_title(("破洞",), 1000) == "破洞1000"


def test_collect_images_assigns_independent_title_sequences(tmp_path) -> None:
    (tmp_path / "1").mkdir()
    (tmp_path / "2").mkdir()
    (tmp_path / "1" / "b.jpg").write_bytes(b"b")
    (tmp_path / "1" / "a.jpg").write_bytes(b"a")
    (tmp_path / "2" / "c.jpg").write_bytes(b"c")

    images = collect_images(tmp_path)

    assert [(item.relative_path, item.desired_title) for item in images] == [
        ("1/a.jpg", "破洞001"),
        ("1/b.jpg", "破洞002"),
        ("2/c.jpg", "水渍-油渍-污渍001"),
    ]


def test_update_title_preserves_existing_status_category_and_tags(tmp_path) -> None:
    patch_payloads: list[dict[str, object]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        if request.method == "GET" and request.url.path == "/api/images/img-1":
            return httpx.Response(
                200,
                json={
                    "id": "img-1",
                    "title": "old-title",
                    "status": "PUBLISHED",
                    "category": {"id": "cat-1", "name": "纺织瑕疵"},
                    "tags": [{"id": "tag-1"}, {"id": "tag-2"}],
                },
            )
        if request.method == "PATCH" and request.url.path == "/api/images/img-1":
            patch_payloads.append(json.loads(request.content))
            return httpx.Response(200, json={"id": "img-1", "title": "破洞001"})
        raise AssertionError(f"unexpected request {request.method} {request.url.path}")

    client = ApiClient(settings(tmp_path), transport=httpx.MockTransport(handler))
    try:
        record = ImportRecord.from_image(image("1/a.jpg"), STATUS_UPLOADED, image_id="img-1")
        updated = update_title_for_record(client, record)
    finally:
        client.close()

    assert updated.title_updated is True
    assert updated.title_error == ""
    assert patch_payloads == [
        {
            "title": "破洞001",
            "status": "PUBLISHED",
            "categoryId": "cat-1",
            "tagIds": ["tag-1", "tag-2"],
        }
    ]


def test_skipped_completed_title_update_keeps_completed_checkpoint_status(tmp_path) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        if request.method == "GET":
            return httpx.Response(
                200,
                json={
                    "id": "img-1",
                    "title": "破洞001",
                    "status": "PUBLISHED",
                    "category": {"id": "cat-1"},
                    "tags": [{"id": "tag-1"}],
                },
            )
        raise AssertionError(f"unexpected request {request.method} {request.url.path}")

    scanned = image("1/a.jpg")
    state = StateStore(tmp_path / "state.jsonl")
    state.append(ImportRecord.from_image(scanned, STATUS_UPLOADED, image_id="img-1"))
    current = ImportRecord.from_image(scanned, STATUS_SKIPPED_COMPLETED, image_id="img-1")
    plan = ImportPlan([scanned], {scanned.relative_path: current}, [])
    client = ApiClient(settings(tmp_path), transport=httpx.MockTransport(handler))
    try:
        updated = update_titles_for_records(client, [current], plan, state)
    finally:
        client.close()

    assert updated[0].title_updated is True
    assert plan.records()[0].status == STATUS_SKIPPED_COMPLETED
    assert state.latest_by_path[scanned.relative_path].status == STATUS_UPLOADED
    assert state.latest_by_path[scanned.relative_path].title_updated is True


def test_keyboard_interrupt_before_confirm_cancels_session() -> None:
    class FakeClient:
        canceled = False

        def create_upload_session(self, **_kwargs):
            return {"id": "session-1", "items": []}

        def stage_upload_item(self, *_args):
            raise KeyboardInterrupt

        def cancel_upload_session(self, session_id):
            self.canceled = session_id == "session-1"
            return {"id": session_id, "status": "CANCELED", "items": []}

    fake = FakeClient()

    with pytest.raises(GracefulInterrupt) as exc_info:
        upload_chunk(fake, "cat-1", [image("1/a.jpg")], ["tag-1"])

    assert fake.canceled is True
    assert exc_info.value.records[0].status == STATUS_INTERRUPTED
    assert exc_info.value.records[0].session_id == "session-1"


def test_keyboard_interrupt_during_confirm_records_confirmed_batch() -> None:
    class FakeClient:
        canceled = False

        def create_upload_session(self, **_kwargs):
            return {"id": "session-1", "items": []}

        def stage_upload_item(self, *_args):
            return {"id": "session-1", "items": [{"status": "STAGED", "candidateImageId": "img-1"}]}

        def confirm_upload_session(self, _session_id):
            raise KeyboardInterrupt

        def upload_session(self, session_id):
            return {
                "id": session_id,
                "status": "CONFIRMED",
                "items": [{"status": "CONFIRMED", "imageId": "img-1"}],
            }

        def cancel_upload_session(self, _session_id):
            self.canceled = True
            return {}

    fake = FakeClient()

    with pytest.raises(GracefulInterrupt) as exc_info:
        upload_chunk(fake, "cat-1", [image("1/a.jpg")], ["tag-1"])

    assert fake.canceled is False
    assert exc_info.value.records[0].status == STATUS_UPLOADED
    assert exc_info.value.records[0].image_id == "img-1"
