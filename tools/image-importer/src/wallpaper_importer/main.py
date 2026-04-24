from __future__ import annotations

from collections.abc import Iterator
from pathlib import Path

from .client import ImportClient
from .mapping import ALLOWED_EXTENSIONS, FOLDER_TO_TAGS
from .settings import settings


def iter_images(data_dir: Path) -> Iterator[tuple[Path, tuple[str, ...]]]:
    for folder, tag_names in FOLDER_TO_TAGS.items():
        folder_path = data_dir / folder
        if not folder_path.is_dir():
            continue

        for file_path in sorted(folder_path.iterdir()):
            if file_path.is_file() and file_path.suffix.lower() in ALLOWED_EXTENSIONS:
                yield file_path, tag_names


def main() -> None:
    total = 0
    success = 0

    client = ImportClient(settings)
    try:
        for file_path, tag_names in iter_images(settings.data_dir):
            total += 1
            label_text = ", ".join(tag_names)
            if settings.dry_run:
                print(f"[dry-run] {file_path} -> {label_text}")
                continue

            response = client.upload_image(file_path, tag_names)
            if response.is_success:
                success += 1
                print(f"[ok] {file_path} -> {label_text}")
            else:
                print(f"[fail] {file_path} -> {response.status_code} {response.text}")
    finally:
        client.close()

    print(f"processed={total} uploaded={success} dry_run={settings.dry_run}")


if __name__ == "__main__":
    main()
