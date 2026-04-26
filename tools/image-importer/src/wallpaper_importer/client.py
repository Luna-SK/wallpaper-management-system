from __future__ import annotations

from pathlib import Path

import httpx

from .settings import Settings


class ImportClient:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.client = httpx.Client(
            base_url=settings.api_base_url.rstrip("/"),
            headers={"Authorization": f"Bearer {settings.access_token}"} if settings.access_token else {},
            timeout=60,
        )

    def upload_image(self, file_path: Path, tag_names: tuple[str, ...]) -> httpx.Response:
        with file_path.open("rb") as file:
            files = {"file": (file_path.name, file)}
            data = {
                "sourcePath": str(file_path),
                "categoryCode": self.settings.default_category_code,
                "categoryName": self.settings.default_category_name,
                "tagGroupCode": self.settings.default_tag_group_code,
                "tagGroupName": self.settings.default_tag_group_name,
                "tagNames": ",".join(tag_names),
            }
            return self.client.post("/imports/images", files=files, data=data)

    def close(self) -> None:
        self.client.close()
