from pathlib import Path

import pytest

from image_uploader.main import (
    GracefulInterrupt,
    collect_images,
    friendly_title,
    upload_chunk,
)
from image_uploader.models import (
    STATUS_INTERRUPTED,
    STATUS_UPLOADED,
    ScannedImage,
)


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


def test_upload_chunk_sends_desired_title() -> None:
    class FakeClient:
        titles: list[str] = []

        def create_upload_session(self, **_kwargs):
            return {"id": "session-1", "items": []}

        def stage_upload_item(self, _session_id, _file_path, title):
            self.titles.append(title)
            return {"id": "session-1", "items": [{"status": "STAGED", "candidateImageId": "img-1"}]}

        def confirm_upload_session(self, session_id):
            return {
                "id": session_id,
                "status": "CONFIRMED",
                "items": [{"status": "CONFIRMED", "imageId": "img-1"}],
            }

        def cancel_upload_session(self, _session_id):
            raise AssertionError("upload session should not be canceled")

    fake = FakeClient()
    batch = upload_chunk(fake, "cat-1", [image("1/a.jpg", "破洞001")], ["tag-1"])

    assert fake.titles == ["破洞001"]
    assert batch["status"] == "CONFIRMED"


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
