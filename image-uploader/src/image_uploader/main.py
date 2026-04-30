from __future__ import annotations

import argparse
import hashlib
import sys
from collections import Counter
from collections.abc import Iterator, Sequence
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any

from pydantic import ValidationError

from .client import ApiClient
from .errors import ImporterError
from .mapping import ALLOWED_EXTENSIONS, ALL_TAG_NAMES, FOLDER_TAGS, FolderTags
from .models import (
    COMPLETED_STATUSES,
    STATUS_DUPLICATE,
    STATUS_FAILED,
    STATUS_INTERRUPTED,
    STATUS_PLANNED,
    STATUS_SKIPPED_COMPLETED,
    STATUS_UPLOADED,
    ImportRecord,
    RETRYABLE_STATUSES,
    ScannedImage,
)
from .report import write_csv_report
from .settings import Settings
from .state import StateStore, state_file_for


JsonObject = dict[str, Any]


@dataclass(frozen=True)
class Taxonomy:
    category_id: str
    tag_group_id: str
    tag_ids_by_name: dict[str, str]


@dataclass
class ImportPlan:
    scanned_images: list[ScannedImage]
    records_by_path: dict[str, ImportRecord]
    upload_images: list[ScannedImage]

    def records(self) -> list[ImportRecord]:
        return [self.records_by_path[image.relative_path] for image in self.scanned_images]

    def update(self, record: ImportRecord) -> None:
        self.records_by_path[record.relative_path] = record


class GracefulInterrupt(Exception):
    def __init__(self, message: str, records: list[ImportRecord] | None = None) -> None:
        super().__init__(message)
        self.records = records or []


def main(argv: Sequence[str] | None = None) -> None:
    try:
        run(argv)
    except GracefulInterrupt as exc:
        sys.stdout.flush()
        print(str(exc), file=sys.stderr)
        raise SystemExit(130) from exc
    except ImporterError as exc:
        sys.stdout.flush()
        print(f"错误：{exc}", file=sys.stderr)
        raise SystemExit(1) from exc
    except KeyboardInterrupt as exc:
        sys.stdout.flush()
        print("已取消。", file=sys.stderr)
        raise SystemExit(130) from exc


def run(argv: Sequence[str] | None = None) -> None:
    settings = load_settings(argv)
    settings.validate_for_run()
    data_dir = settings.resolved_data_dir
    scanned_images = collect_images(data_dir)
    if not scanned_images:
        raise ImporterError(f"没有在 DATA_DIR 的 0 到 20 子目录中找到可导入图片：{data_dir}")

    run_dir = settings.resolved_run_dir
    state_store = StateStore(state_file_for(data_dir, run_dir))
    report_path = settings.resolved_report_file or default_report_file(run_dir)

    with ApiClient(settings) as client:
        client.authenticate()
        deduplication_enabled = client.upload_deduplication_enabled()
        plan = prepare_import_plan(
            scanned_images,
            state_store.latest_by_path,
            skip_completed=settings.skip_completed,
            retry_failed=settings.retry_failed,
        )
        print_header(settings, data_dir, state_store.path, report_path, plan, deduplication_enabled)
        taxonomy = load_taxonomy(client, settings)

        if settings.dry_run:
            write_csv_report(report_path, plan.records())
            print(f"DRY_RUN=true，仅完成配置、认证和分类法校验，没有上传图片。报告：{report_path}")
            return

        try:
            upload_planned_images(client, settings, taxonomy, plan, state_store)
        except GracefulInterrupt:
            write_csv_report(report_path, plan.records())
            print(f"已写入中断报告：{report_path}")
            raise

    write_csv_report(report_path, plan.records())
    print_summary(plan.records(), report_path)


def load_settings(argv: Sequence[str] | None = None) -> Settings:
    args = parse_args(argv)
    try:
        settings = Settings()
    except ValidationError as exc:
        errors = "; ".join(error["msg"] for error in exc.errors())
        raise ImporterError(f".env 配置格式不正确：{errors}") from exc

    updates: dict[str, object] = {}
    if args.skip_completed is not None:
        updates["skip_completed"] = args.skip_completed
    if args.retry_failed is not None:
        updates["retry_failed"] = args.retry_failed
    if args.run_dir is not None:
        updates["run_dir"] = Path(args.run_dir)
    if args.report is not None:
        updates["report_file"] = Path(args.report)
    if args.dry_run is not None:
        updates["dry_run"] = args.dry_run
    return settings.model_copy(update=updates)


def parse_args(argv: Sequence[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Import textile defect images into the wallpaper manager.")
    skip_completed = parser.add_mutually_exclusive_group()
    skip_completed.add_argument(
        "--skip-completed",
        dest="skip_completed",
        action="store_true",
        default=None,
        help="skip unchanged completed checkpoint records",
    )
    skip_completed.add_argument(
        "--no-skip-completed",
        dest="skip_completed",
        action="store_false",
        default=None,
        help="upload all scanned files instead of skipping completed checkpoint records",
    )
    parser.add_argument("--retry-failed", action="store_true", default=None, help="only retry unchanged failed files")
    parser.add_argument("--run-dir", help="directory for checkpoint and generated reports")
    parser.add_argument("--report", help="CSV report path")
    dry_run = parser.add_mutually_exclusive_group()
    dry_run.add_argument("--dry-run", dest="dry_run", action="store_true", default=None, help="preview only")
    dry_run.add_argument("--no-dry-run", dest="dry_run", action="store_false", help="upload files")
    return parser.parse_args(argv)


def collect_images(data_dir: Path) -> list[ScannedImage]:
    images: list[ScannedImage] = []
    title_counts: Counter[tuple[str, ...]] = Counter()
    for item in FOLDER_TAGS:
        folder_path = data_dir / item.folder
        if not folder_path.is_dir():
            continue
        file_paths = sorted(
            (
                path
                for path in folder_path.rglob("*")
                if path.is_file() and path.suffix.lower() in ALLOWED_EXTENSIONS
            ),
            key=lambda path: path.as_posix(),
        )
        for file_path in file_paths:
            title_counts[item.tag_names] += 1
            try:
                stat = file_path.stat()
            except OSError as exc:
                raise ImporterError(f"无法读取图片文件信息：{file_path}") from exc
            images.append(
                ScannedImage(
                    file_path=file_path,
                    relative_path=file_path.relative_to(data_dir).as_posix(),
                    folder=item.folder,
                    tag_names=item.tag_names,
                    desired_title=friendly_title(item.tag_names, title_counts[item.tag_names]),
                    size_bytes=stat.st_size,
                    mtime_ns=stat.st_mtime_ns,
                    sha256=file_sha256(file_path),
                )
            )
    return images


def friendly_title(tag_names: tuple[str, ...], sequence: int) -> str:
    prefix = "-".join(tag_names)
    return f"{prefix}{sequence:03d}"


def file_sha256(file_path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with file_path.open("rb") as file:
            for chunk in iter(lambda: file.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as exc:
        raise ImporterError(f"无法读取图片文件：{file_path}") from exc
    return digest.hexdigest()


def prepare_import_plan(
    scanned_images: list[ScannedImage],
    latest_records: dict[str, ImportRecord],
    *,
    skip_completed: bool,
    retry_failed: bool,
) -> ImportPlan:
    records_by_path: dict[str, ImportRecord] = {}
    upload_images: list[ScannedImage] = []

    for image in scanned_images:
        previous = latest_records.get(image.relative_path)
        matching_previous = previous if previous and previous.matches(image) else None

        if retry_failed:
            if matching_previous and matching_previous.status in RETRYABLE_STATUSES:
                records_by_path[image.relative_path] = ImportRecord.from_image(image, STATUS_PLANNED)
                upload_images.append(image)
            elif matching_previous and matching_previous.status in COMPLETED_STATUSES:
                records_by_path[image.relative_path] = ImportRecord.from_image(
                    image,
                    STATUS_SKIPPED_COMPLETED,
                    image_id=matching_previous.image_id,
                )
            else:
                records_by_path[image.relative_path] = ImportRecord.from_image(image, STATUS_PLANNED)
            continue

        if skip_completed and matching_previous and matching_previous.status in COMPLETED_STATUSES:
            records_by_path[image.relative_path] = ImportRecord.from_image(
                image,
                STATUS_SKIPPED_COMPLETED,
                image_id=matching_previous.image_id,
            )
            continue

        records_by_path[image.relative_path] = ImportRecord.from_image(image, STATUS_PLANNED)
        upload_images.append(image)

    return ImportPlan(scanned_images=scanned_images, records_by_path=records_by_path, upload_images=upload_images)


def print_header(
    settings: Settings,
    data_dir: Path,
    state_path: Path,
    report_path: Path,
    plan: ImportPlan,
    deduplication_enabled: bool,
) -> None:
    mode = "预览" if settings.dry_run else "真实上传"
    counts = Counter(record.status for record in plan.records())
    print(f"API: {settings.normalized_api_base_url}")
    print(f"数据目录: {data_dir}")
    print(f"运行模式: {mode}")
    print(f"批次大小: {settings.batch_size}")
    print(f"上传去重: {deduplication_enabled}")
    print(f"跳过已完成: {settings.skip_completed}")
    print(f"只重试失败: {settings.retry_failed}")
    print(f"状态文件: {state_path}")
    print(f"报告文件: {report_path}")
    print(f"发现图片: {len(plan.scanned_images)}")
    print(
        "计划: "
        f"upload={len(plan.upload_images)} "
        f"planned={counts.get(STATUS_PLANNED, 0)} "
        f"skipped_completed={counts.get(STATUS_SKIPPED_COMPLETED, 0)}"
    )
    print("目录映射:")
    for item in FOLDER_TAGS:
        count = sum(1 for image in plan.scanned_images if image.folder == item.folder)
        if count:
            print(f"  {item.folder}: {count} 张 -> {', '.join(item.tag_names)}")


def load_taxonomy(client: ApiClient, settings: Settings) -> Taxonomy:
    category = find_seed_resource(
        client.categories(),
        settings.category_code,
        settings.category_name,
        "分类",
    )
    group = find_seed_resource(
        client.tag_groups(),
        settings.tag_group_code,
        settings.tag_group_name,
        "标签组",
    )
    tags = client.tags(str(group["id"]))
    tags_by_name = {str(tag.get("name")): tag for tag in tags}

    missing_tags = [name for name in ALL_TAG_NAMES if name not in tags_by_name]
    disabled_tags = [name for name in ALL_TAG_NAMES if name in tags_by_name and not tags_by_name[name].get("enabled")]
    missing_ids = [name for name in ALL_TAG_NAMES if name in tags_by_name and not tags_by_name[name].get("id")]
    if missing_tags or disabled_tags:
        parts: list[str] = []
        if missing_tags:
            parts.append("缺失标签：" + ", ".join(missing_tags))
        if disabled_tags:
            parts.append("已停用标签：" + ", ".join(disabled_tags))
        raise ImporterError("项目种子标签不完整，导入脚本不会自动创建或启用标签。" + "；".join(parts))
    if missing_ids:
        raise ImporterError("项目种子标签响应缺少 id：" + ", ".join(missing_ids))

    return Taxonomy(
        category_id=str(category["id"]),
        tag_group_id=str(group["id"]),
        tag_ids_by_name={name: str(tags_by_name[name]["id"]) for name in ALL_TAG_NAMES},
    )


def find_seed_resource(resources: list[JsonObject], code: str, name: str, label: str) -> JsonObject:
    resource = next((item for item in resources if item.get("code") == code), None)
    if resource is None:
        raise ImporterError(f"项目种子{label}不存在：{code} / {name}")
    if resource.get("name") != name:
        raise ImporterError(
            f"项目种子{label}名称不匹配：code={code}，期望名称 {name}，实际名称 {resource.get('name')}"
        )
    if not resource.get("enabled"):
        raise ImporterError(f"项目种子{label}已停用：{code} / {name}")
    if not resource.get("id"):
        raise ImporterError(f"项目种子{label}响应缺少 id：{code} / {name}")
    return resource


def upload_planned_images(
    client: ApiClient,
    settings: Settings,
    taxonomy: Taxonomy,
    plan: ImportPlan,
    state_store: StateStore,
) -> None:
    for item in FOLDER_TAGS:
        images = [image for image in plan.upload_images if image.folder == item.folder]
        if not images:
            continue
        tag_ids = [taxonomy.tag_ids_by_name[name] for name in item.tag_names]
        upload_folder(client, settings, item, images, taxonomy.category_id, tag_ids, plan, state_store)


def upload_folder(
    client: ApiClient,
    settings: Settings,
    item: FolderTags,
    images: Sequence[ScannedImage],
    category_id: str,
    tag_ids: list[str],
    plan: ImportPlan,
    state_store: StateStore,
) -> None:
    chunks = list(chunked(images, settings.batch_size))
    for index, chunk in enumerate(chunks, start=1):
        label_text = ", ".join(item.tag_names)
        print(f"[上传] 目录 {item.folder} 批次 {index}/{len(chunks)}: {len(chunk)} 张 -> {label_text}")
        try:
            batch = upload_chunk(client, category_id, chunk, tag_ids)
            records = records_from_batch(chunk, batch)
        except GracefulInterrupt as exc:
            for record in exc.records:
                plan.update(record)
            state_store.append_many(exc.records)
            raise
        except ImporterError as exc:
            records = [
                ImportRecord.from_image(image, STATUS_FAILED, error_message=str(exc))
                for image in chunk
            ]
        for record in records:
            plan.update(record)
        state_store.append_many(records)
        print_batch_summary(records)


def upload_chunk(client: ApiClient, category_id: str, images: Sequence[ScannedImage], tag_ids: list[str]) -> JsonObject:
    session_id = ""
    confirm_started = False
    try:
        batch = client.create_upload_session(
            category_id=category_id,
            tag_ids=tag_ids,
            total_count=len(images),
        )
        if not batch.get("id"):
            raise ImporterError("创建上传会话后端响应缺少 id。")
        session_id = str(batch["id"])
        for image in images:
            batch = client.stage_upload_item(session_id, image.file_path, image.desired_title)
    except ImporterError:
        if session_id:
            try:
                client.cancel_upload_session(session_id)
            except ImporterError:
                pass
        raise
    except KeyboardInterrupt as exc:
        handle_upload_interrupt(client, images, session_id, confirm_started, exc)

    items = batch.get("items") if isinstance(batch.get("items"), list) else []
    if not any(item.get("status") in {"STAGED", "DUPLICATE"} for item in items if isinstance(item, dict)):
        return client.cancel_upload_session(session_id)
    confirm_started = True
    try:
        return client.confirm_upload_session(session_id)
    except KeyboardInterrupt as exc:
        handle_upload_interrupt(client, images, session_id, confirm_started, exc)


def handle_upload_interrupt(
    client: ApiClient,
    images: Sequence[ScannedImage],
    session_id: str,
    confirm_started: bool,
    exc: KeyboardInterrupt,
) -> None:
    records = interrupted_records(images, session_id, "用户中断，未确认当前上传会话。")
    if session_id and confirm_started:
        try:
            current = client.upload_session(session_id)
        except ImporterError as lookup_exc:
            records = interrupted_records(
                images,
                session_id,
                f"用户中断；确认状态查询失败：{lookup_exc}",
            )
            raise GracefulInterrupt("已取消；当前批次状态未知，已在报告中记录 sessionId。", records) from exc
        if current.get("status") == "CONFIRMED":
            records = records_from_batch(images, current)
            raise GracefulInterrupt("已取消；当前批次已被后端确认，已写入确认结果。", records) from exc
        try:
            client.cancel_upload_session(session_id)
            records = interrupted_records(images, session_id, "用户中断，已取消当前上传会话。")
        except ImporterError as cancel_exc:
            records = interrupted_records(
                images,
                session_id,
                f"用户中断；取消上传会话失败：{cancel_exc}",
            )
        raise GracefulInterrupt("已取消；当前上传会话已处理，详情见报告。", records) from exc
    if session_id:
        try:
            client.cancel_upload_session(session_id)
            records = interrupted_records(images, session_id, "用户中断，已取消当前上传会话。")
        except ImporterError as cancel_exc:
            records = interrupted_records(
                images,
                session_id,
                f"用户中断；取消上传会话失败：{cancel_exc}",
            )
    raise GracefulInterrupt("已取消；当前上传会话已处理，详情见报告。", records) from exc


def records_from_batch(images: Sequence[ScannedImage], batch: JsonObject) -> list[ImportRecord]:
    session_id = str(batch.get("id") or "")
    raw_items = batch.get("items")
    items = raw_items if isinstance(raw_items, list) else []
    records: list[ImportRecord] = []
    for index, image in enumerate(images):
        item = items[index] if index < len(items) and isinstance(items[index], dict) else {}
        item_status = item.get("status")
        image_id = str(item.get("imageId") or item.get("candidateImageId") or "")
        if item_status == "CONFIRMED":
            records.append(ImportRecord.from_image(image, STATUS_UPLOADED, session_id=session_id, image_id=image_id))
        elif item_status == "DUPLICATE":
            records.append(ImportRecord.from_image(image, STATUS_DUPLICATE, session_id=session_id, image_id=image_id))
        elif item_status == "FAILED":
            records.append(
                ImportRecord.from_image(
                    image,
                    STATUS_FAILED,
                    session_id=session_id,
                    error_message=str(item.get("errorMessage") or "上传失败"),
                )
            )
        else:
            records.append(
                ImportRecord.from_image(
                    image,
                    STATUS_FAILED,
                    session_id=session_id,
                    error_message=f"后端返回未知上传状态：{item_status or 'missing'}",
                )
            )
    return records


def interrupted_records(images: Sequence[ScannedImage], session_id: str, message: str) -> list[ImportRecord]:
    return [
        ImportRecord.from_image(
            image,
            STATUS_INTERRUPTED,
            session_id=session_id,
            error_message=message,
        )
        for image in images
    ]


def print_batch_summary(records: list[ImportRecord]) -> None:
    counts = Counter(record.status for record in records)
    print(
        "  完成: "
        f"uploaded={counts.get(STATUS_UPLOADED, 0)} "
        f"duplicate={counts.get(STATUS_DUPLICATE, 0)} "
        f"failed={counts.get(STATUS_FAILED, 0)}"
    )
    for record in records:
        if record.status == STATUS_FAILED:
            print(f"  [失败] {record.relative_path}: {record.error_message or '上传失败'}")


def print_summary(records: list[ImportRecord], report_path: Path) -> None:
    counts = Counter(record.status for record in records)
    print(
        "导入完成："
        f"uploaded={counts.get(STATUS_UPLOADED, 0)} "
        f"duplicate={counts.get(STATUS_DUPLICATE, 0)} "
        f"failed={counts.get(STATUS_FAILED, 0)} "
        f"interrupted={counts.get(STATUS_INTERRUPTED, 0)} "
        f"skipped_completed={counts.get(STATUS_SKIPPED_COMPLETED, 0)}"
    )
    print(f"报告：{report_path}")


def chunked(items: Sequence[ScannedImage], size: int) -> Iterator[list[ScannedImage]]:
    for start in range(0, len(items), size):
        yield list(items[start : start + size])


def default_report_file(run_dir: Path) -> Path:
    timestamp = datetime.now().strftime("%Y%m%d%H%M%S%f")
    return run_dir / f"report-{timestamp}.csv"


if __name__ == "__main__":
    main()
