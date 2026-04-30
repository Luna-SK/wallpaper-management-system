from __future__ import annotations

import csv
from pathlib import Path

from .models import ImportRecord


REPORT_FIELDS = (
    "relativePath",
    "folder",
    "tagNames",
    "desiredTitle",
    "sizeBytes",
    "mtimeNs",
    "sha256",
    "status",
    "sessionId",
    "imageId",
    "errorMessage",
    "updatedAt",
)


def write_csv_report(path: Path, records: list[ImportRecord]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=REPORT_FIELDS)
        writer.writeheader()
        for record in records:
            row = record.to_report_row()
            row["tagNames"] = ",".join(record.tag_names)
            writer.writerow(row)
