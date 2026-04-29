from __future__ import annotations

import hashlib
import json
from pathlib import Path

from .errors import ImporterError
from .models import ImportRecord


def state_file_for(data_dir: Path, run_dir: Path) -> Path:
    digest = hashlib.sha256(str(data_dir).encode("utf-8")).hexdigest()[:16]
    return run_dir / f"state-{digest}.jsonl"


class StateStore:
    def __init__(self, path: Path) -> None:
        self.path = path
        self.latest_by_path = self._load_latest()

    def append(self, record: ImportRecord) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.path.open("a", encoding="utf-8") as file:
            file.write(json.dumps(record.to_json(), ensure_ascii=False, separators=(",", ":")) + "\n")
        self.latest_by_path[record.relative_path] = record

    def append_many(self, records: list[ImportRecord]) -> None:
        if not records:
            return
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.path.open("a", encoding="utf-8") as file:
            for record in records:
                file.write(json.dumps(record.to_json(), ensure_ascii=False, separators=(",", ":")) + "\n")
                self.latest_by_path[record.relative_path] = record

    def _load_latest(self) -> dict[str, ImportRecord]:
        latest: dict[str, ImportRecord] = {}
        if not self.path.exists():
            return latest
        with self.path.open("r", encoding="utf-8") as file:
            for line in file:
                line = line.strip()
                if not line:
                    continue
                try:
                    data = json.loads(line)
                except json.JSONDecodeError as exc:
                    raise ImporterError(f"状态文件格式不正确：{self.path}") from exc
                if isinstance(data, dict):
                    record = ImportRecord.from_json(data)
                    if record.relative_path:
                        latest[record.relative_path] = record
        return latest
