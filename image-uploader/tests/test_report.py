import csv

from image_uploader.models import STATUS_PLANNED, ImportRecord
from image_uploader.report import REPORT_FIELDS, write_csv_report


def test_write_csv_report_contains_expected_fields(tmp_path) -> None:
    report_path = tmp_path / "report.csv"
    record = ImportRecord(
        relative_path="0/a.jpg",
        folder="0",
        tag_names=("无疵点",),
        desired_title="无疵点001",
        size_bytes=3,
        mtime_ns=10,
        sha256="abc",
        status=STATUS_PLANNED,
    )

    write_csv_report(report_path, [record])

    with report_path.open("r", encoding="utf-8-sig", newline="") as file:
        rows = list(csv.DictReader(file))

    assert rows[0]["relativePath"] == "0/a.jpg"
    assert rows[0]["tagNames"] == "无疵点"
    assert rows[0]["desiredTitle"] == "无疵点001"
    assert tuple(rows[0].keys()) == REPORT_FIELDS
