from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class FolderTags:
    folder: str
    tag_names: tuple[str, ...]


FOLDER_TAGS: tuple[FolderTags, ...] = (
    FolderTags("0", ("无疵点",)),
    FolderTags("1", ("破洞",)),
    FolderTags("2", ("水渍", "油渍", "污渍")),
    FolderTags("3", ("三丝",)),
    FolderTags("4", ("结头",)),
    FolderTags("5", ("花板跳",)),
    FolderTags("6", ("百脚",)),
    FolderTags("7", ("毛粒",)),
    FolderTags("8", ("粗经",)),
    FolderTags("9", ("松经",)),
    FolderTags("10", ("断经",)),
    FolderTags("11", ("吊经",)),
    FolderTags("12", ("粗维",)),
    FolderTags("13", ("纬缩",)),
    FolderTags("14", ("浆斑",)),
    FolderTags("15", ("整经结",)),
    FolderTags("16", ("星跳", "跳花")),
    FolderTags("17", ("断氨纶",)),
    FolderTags("18", ("稀密档", "浪纹档", "色差档")),
    FolderTags("19", ("磨痕", "轧痕", "修痕", "烧毛痕")),
    FolderTags("20", ("死皱", "云织", "双纬", "双经", "跳纱", "筘路", "纬纱不良")),
)

FOLDER_TO_TAGS: dict[str, tuple[str, ...]] = {item.folder: item.tag_names for item in FOLDER_TAGS}
ALL_TAG_NAMES: tuple[str, ...] = tuple(dict.fromkeys(name for item in FOLDER_TAGS for name in item.tag_names))
ALLOWED_EXTENSIONS = frozenset({".jpg", ".jpeg", ".png", ".bmp", ".gif", ".webp"})
