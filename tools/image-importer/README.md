# Image Importer

批量导入墙布图片的辅助工具。它只调用新系统 API，不直连数据库。

## Directory Convention

一期保留既有数据集目录习惯：`DATA_DIR` 下用 `0` 到 `20` 文件夹表达纺织瑕疵图片集合。工具会把文件夹编号转换为 `纺织瑕疵` 分类和 `瑕疵` 标签组下的标签，并提交给后端导入接口。

## Run

```bash
cp .env.example .env
uv sync
uv run wallpaper-import
```

默认 `DRY_RUN=true`，会打印将要导入的文件和标签，不会发起上传。
