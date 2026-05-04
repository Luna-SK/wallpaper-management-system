from pathlib import Path

from image_uploader.main import prepare_import_plan
from image_uploader.models import (
    STATUS_FAILED,
    STATUS_INTERRUPTED,
    STATUS_PLANNED,
    STATUS_SKIPPED_COMPLETED,
    STATUS_SKIPPED_NOT_RETRYABLE,
    STATUS_UPLOADED,
    ImportRecord,
    ScannedImage,
)
from image_uploader.state import StateStore


def image(relative_path: str, sha256: str = "sha") -> ScannedImage:
    folder = relative_path.split("/", 1)[0]
    return ScannedImage(
        file_path=Path("/data") / relative_path,
        relative_path=relative_path,
        folder=folder,
        tag_names=("无疵点",),
        desired_title="无疵点001",
        size_bytes=10,
        mtime_ns=100,
        sha256=sha256,
    )


def test_state_store_appends_and_loads_latest_record(tmp_path) -> None:
    path = tmp_path / "state.jsonl"
    first = ImportRecord.from_image(image("0/a.jpg"), STATUS_FAILED, error_message="old")
    latest = ImportRecord.from_image(image("0/a.jpg"), STATUS_UPLOADED, image_id="img-1")

    store = StateStore(path)
    store.append(first)
    store.append(latest)
    reloaded = StateStore(path)

    assert reloaded.latest_by_path["0/a.jpg"].status == STATUS_UPLOADED
    assert reloaded.latest_by_path["0/a.jpg"].image_id == "img-1"


def test_skip_completed_skips_completed_unchanged_file() -> None:
    scanned = [image("0/a.jpg")]
    latest = {
        "0/a.jpg": ImportRecord.from_image(scanned[0], STATUS_UPLOADED, image_id="img-1"),
    }

    plan = prepare_import_plan(scanned, latest, skip_completed=True, retry_failed=False)

    assert plan.upload_images == []
    assert plan.records()[0].status == STATUS_SKIPPED_COMPLETED
    assert plan.records()[0].image_id == "img-1"


def test_retry_failed_only_selects_failed_records() -> None:
    failed = image("0/a.jpg", "a")
    uploaded = image("1/b.jpg", "b")
    latest = {
        failed.relative_path: ImportRecord.from_image(failed, STATUS_FAILED),
        uploaded.relative_path: ImportRecord.from_image(uploaded, STATUS_UPLOADED),
    }

    plan = prepare_import_plan([failed, uploaded], latest, skip_completed=False, retry_failed=True)

    assert plan.upload_images == [failed]
    assert plan.records_by_path[failed.relative_path].status == STATUS_PLANNED
    assert plan.records_by_path[uploaded.relative_path].status == STATUS_SKIPPED_COMPLETED


def test_retry_failed_skips_files_without_retryable_checkpoint() -> None:
    no_checkpoint = image("0/a.jpg", "a")
    changed = image("1/b.jpg", "b")
    latest = {
        changed.relative_path: ImportRecord(
            relative_path=changed.relative_path,
            folder=changed.folder,
            tag_names=changed.tag_names,
            desired_title=changed.desired_title,
            size_bytes=changed.size_bytes + 1,
            mtime_ns=changed.mtime_ns,
            sha256=changed.sha256,
            status=STATUS_FAILED,
        ),
    }

    plan = prepare_import_plan([no_checkpoint, changed], latest, skip_completed=False, retry_failed=True)

    assert plan.upload_images == []
    assert plan.records_by_path[no_checkpoint.relative_path].status == STATUS_SKIPPED_NOT_RETRYABLE
    assert plan.records_by_path[changed.relative_path].status == STATUS_SKIPPED_NOT_RETRYABLE


def test_retry_failed_selects_interrupted_records() -> None:
    interrupted = image("0/a.jpg", "a")
    latest = {
        interrupted.relative_path: ImportRecord.from_image(interrupted, STATUS_INTERRUPTED),
    }

    plan = prepare_import_plan([interrupted], latest, skip_completed=False, retry_failed=True)

    assert plan.upload_images == [interrupted]
    assert plan.records_by_path[interrupted.relative_path].status == STATUS_PLANNED


def test_same_sha_files_are_all_planned_for_backend_deduplication() -> None:
    first = image("0/a.jpg", "same")
    duplicate = image("1/b.jpg", "same")

    plan = prepare_import_plan([first, duplicate], {}, skip_completed=False, retry_failed=False)

    assert plan.upload_images == [first, duplicate]
    assert plan.records_by_path[first.relative_path].status == STATUS_PLANNED
    assert plan.records_by_path[duplicate.relative_path].status == STATUS_PLANNED
