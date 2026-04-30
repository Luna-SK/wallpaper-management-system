from pathlib import Path

from image_uploader.main import ImportPlan, append_skipped_records, prepare_import_plan
from image_uploader.models import (
    STATUS_FAILED,
    STATUS_INTERRUPTED,
    STATUS_PLANNED,
    STATUS_SKIPPED_COMPLETED,
    STATUS_SKIPPED_LOCAL_DUPLICATE,
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


def test_resume_skips_completed_unchanged_file() -> None:
    scanned = [image("0/a.jpg")]
    latest = {
        "0/a.jpg": ImportRecord.from_image(scanned[0], STATUS_UPLOADED, image_id="img-1"),
    }

    plan = prepare_import_plan(scanned, latest, resume=True, retry_failed=False)

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

    plan = prepare_import_plan([failed, uploaded], latest, resume=False, retry_failed=True)

    assert plan.upload_images == [failed]
    assert plan.records_by_path[failed.relative_path].status == STATUS_PLANNED
    assert plan.records_by_path[uploaded.relative_path].status == STATUS_SKIPPED_COMPLETED


def test_retry_failed_selects_interrupted_records() -> None:
    interrupted = image("0/a.jpg", "a")
    latest = {
        interrupted.relative_path: ImportRecord.from_image(interrupted, STATUS_INTERRUPTED),
    }

    plan = prepare_import_plan([interrupted], latest, resume=False, retry_failed=True)

    assert plan.upload_images == [interrupted]
    assert plan.records_by_path[interrupted.relative_path].status == STATUS_PLANNED


def test_local_duplicate_is_skipped() -> None:
    first = image("0/a.jpg", "same")
    duplicate = image("1/b.jpg", "same")

    plan = prepare_import_plan([first, duplicate], {}, resume=False, retry_failed=False)

    assert plan.upload_images == [first]
    assert plan.records_by_path[duplicate.relative_path].status == STATUS_SKIPPED_LOCAL_DUPLICATE


def test_local_duplicate_is_planned_when_deduplication_disabled() -> None:
    first = image("0/a.jpg", "same")
    duplicate = image("1/b.jpg", "same")

    plan = prepare_import_plan([first, duplicate], {}, resume=False, retry_failed=False, deduplication_enabled=False)

    assert plan.upload_images == [first, duplicate]
    assert plan.records_by_path[first.relative_path].status == STATUS_PLANNED
    assert plan.records_by_path[duplicate.relative_path].status == STATUS_PLANNED


def test_local_duplicate_is_skipped_when_deduplication_enabled() -> None:
    first = image("0/a.jpg", "same")
    duplicate = image("1/b.jpg", "same")

    plan = prepare_import_plan([first, duplicate], {}, resume=False, retry_failed=False, deduplication_enabled=True)

    assert plan.upload_images == [first]
    assert plan.records_by_path[duplicate.relative_path].status == STATUS_SKIPPED_LOCAL_DUPLICATE


def test_resume_skip_does_not_overwrite_completed_checkpoint(tmp_path) -> None:
    path = tmp_path / "state.jsonl"
    store = StateStore(path)
    completed = image("0/a.jpg", "a")
    duplicate = image("1/b.jpg", "b")
    uploaded_record = ImportRecord.from_image(completed, STATUS_UPLOADED, image_id="img-1")
    skipped_completed = ImportRecord.from_image(completed, STATUS_SKIPPED_COMPLETED)
    skipped_duplicate = ImportRecord.from_image(duplicate, STATUS_SKIPPED_LOCAL_DUPLICATE)
    store.append(uploaded_record)

    plan = ImportPlan(
        scanned_images=[completed, duplicate],
        records_by_path={
            completed.relative_path: skipped_completed,
            duplicate.relative_path: skipped_duplicate,
        },
        upload_images=[],
    )
    append_skipped_records(store, plan)
    reloaded = StateStore(path)

    assert reloaded.latest_by_path[completed.relative_path].status == STATUS_UPLOADED
    assert reloaded.latest_by_path[completed.relative_path].image_id == "img-1"
    assert reloaded.latest_by_path[duplicate.relative_path].status == STATUS_SKIPPED_LOCAL_DUPLICATE
