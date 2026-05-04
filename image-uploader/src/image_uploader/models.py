from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path


STATUS_PLANNED = "planned"
STATUS_UPLOADED = "uploaded"
STATUS_DUPLICATE = "duplicate"
STATUS_FAILED = "failed"
STATUS_INTERRUPTED = "interrupted"
STATUS_SKIPPED_COMPLETED = "skipped_completed"
STATUS_SKIPPED_NOT_RETRYABLE = "skipped_not_retryable"

COMPLETED_STATUSES = frozenset({STATUS_UPLOADED, STATUS_DUPLICATE})
RETRYABLE_STATUSES = frozenset({STATUS_FAILED, STATUS_INTERRUPTED})


@dataclass(frozen=True)
class ScannedImage:
    file_path: Path
    relative_path: str
    folder: str
    tag_names: tuple[str, ...]
    desired_title: str
    size_bytes: int
    mtime_ns: int
    sha256: str


@dataclass(frozen=True)
class ImportRecord:
    relative_path: str
    folder: str
    tag_names: tuple[str, ...]
    desired_title: str
    size_bytes: int
    mtime_ns: int
    sha256: str
    status: str
    session_id: str = ""
    image_id: str = ""
    error_message: str = ""
    updated_at: str = field(default_factory=lambda: datetime.now(UTC).isoformat())

    @classmethod
    def from_image(
        cls,
        image: ScannedImage,
        status: str,
        *,
        session_id: str = "",
        image_id: str = "",
        error_message: str = "",
    ) -> "ImportRecord":
        return cls(
            relative_path=image.relative_path,
            folder=image.folder,
            tag_names=image.tag_names,
            desired_title=image.desired_title,
            size_bytes=image.size_bytes,
            mtime_ns=image.mtime_ns,
            sha256=image.sha256,
            status=status,
            session_id=session_id,
            image_id=image_id,
            error_message=error_message,
        )

    def matches(self, image: ScannedImage) -> bool:
        return (
            self.relative_path == image.relative_path
            and self.size_bytes == image.size_bytes
            and self.mtime_ns == image.mtime_ns
            and self.sha256 == image.sha256
        )

    def to_json(self) -> dict[str, object]:
        return {
            "relativePath": self.relative_path,
            "folder": self.folder,
            "tagNames": list(self.tag_names),
            "desiredTitle": self.desired_title,
            "sizeBytes": self.size_bytes,
            "mtimeNs": self.mtime_ns,
            "sha256": self.sha256,
            "status": self.status,
            "sessionId": self.session_id,
            "imageId": self.image_id,
            "errorMessage": self.error_message,
            "updatedAt": self.updated_at,
        }

    @classmethod
    def from_json(cls, data: dict[str, object]) -> "ImportRecord":
        tag_names = data.get("tagNames") or ()
        if isinstance(tag_names, str):
            tag_names = tuple(name.strip() for name in tag_names.split(",") if name.strip())
        return cls(
            relative_path=str(data.get("relativePath") or ""),
            folder=str(data.get("folder") or ""),
            tag_names=tuple(str(name) for name in tag_names),
            desired_title=str(data.get("desiredTitle") or ""),
            size_bytes=int(data.get("sizeBytes") or 0),
            mtime_ns=int(data.get("mtimeNs") or 0),
            sha256=str(data.get("sha256") or ""),
            status=str(data.get("status") or STATUS_FAILED),
            session_id=str(data.get("sessionId") or ""),
            image_id=str(data.get("imageId") or ""),
            error_message=str(data.get("errorMessage") or ""),
            updated_at=str(data.get("updatedAt") or ""),
        )

    def to_report_row(self) -> dict[str, object]:
        return self.to_json()
