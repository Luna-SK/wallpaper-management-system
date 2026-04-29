# Image Uploader

面向新项目的图片导入工具。它复用老项目 `0` 到 `20` 文件夹与标签映射，但只调用新系统 API，不直连数据库。

## 前置条件

- Python 3.11+
- [uv](https://docs.astral.sh/uv/)
- 已启动的新项目后端服务
- 账号至少需要 `image:upload` 权限，并能访问分类与标签列表接口

## 快速开始

```bash
cd image-uploader
cp .env.example .env
uv sync
uv run image-uploader
```

默认 `DRY_RUN=true`，只会检查配置、登录后端、校验项目种子分类法，并输出将要导入的目录摘要，不会上传图片。dry-run 也会生成 CSV 报告，方便先审查导入计划。

确认无误后，把 `.env` 中的 `DRY_RUN=false`，再执行：

```bash
uv run image-uploader
```

也可以用命令行覆盖 `.env`：

```bash
uv run image-uploader --no-dry-run
```

## 配置

| 变量 | 说明 |
|------|------|
| `API_BASE_URL` | 后端 API 地址，本地默认 `http://localhost:18090/api` |
| `USERNAME` / `PASSWORD` | 推荐配置；脚本会自动登录并维护 `Authorization` 请求头 |
| `AUTHORIZATION_HEADER` | 高级用法，完整 HTTP 认证头，例如 `Bearer eyJ...` |
| `DATA_DIR` | 图片数据目录的绝对路径 |
| `CATEGORY_CODE` / `CATEGORY_NAME` | 必须已存在的分类，默认 `TEXTILE_DEFECT / 纺织瑕疵` |
| `TAG_GROUP_CODE` / `TAG_GROUP_NAME` | 必须已存在的标签组，默认 `DEFECT / 瑕疵` |
| `DRY_RUN` | `true` 为预览模式，`false` 才真实上传 |
| `BATCH_SIZE` | 每个上传会话包含的最大图片数，默认 `50` |
| `RESUME` | `true` 时跳过本地状态中已成功上传或已确认重复且文件未变化的图片 |
| `RETRY_FAILED` | `true` 时只重试上次失败且文件未变化的图片 |
| `RUN_DIR` | 本地 checkpoint 和自动报告目录，默认 `.import-runs` |
| `REPORT_FILE` | 指定 CSV 报告路径；留空时自动生成 `report-<timestamp>.csv` |

本机 Docker 推荐只填写 `USERNAME=admin` 和一键部署脚本输出的 `admin` 密码，不需要手动复制令牌。若确需手动认证，请填写完整的 `AUTHORIZATION_HEADER`；旧版 `.env` 中的 `ACCESS_TOKEN` 仍会兼容读取，但不再推荐使用。

大量上传时，脚本使用 `USERNAME/PASSWORD` 登录后会在访问令牌过期时自动调用 `/api/auth/refresh` 续期，并重试一次原请求。手动 `AUTHORIZATION_HEADER` 无法自动续期，过期后需要重新填写。

## 断点续传与失败重试

真实上传时，工具会把每个文件的最终状态追加写入本地 JSONL checkpoint：

```text
.import-runs/state-<data_dir_hash>.jsonl
```

状态匹配使用 `relativePath + sizeBytes + mtimeNs + sha256`，文件内容或时间变化后不会被误跳过。

常用命令：

```bash
# 跳过此前已成功上传或已确认重复的同一文件
uv run image-uploader --resume --no-dry-run

# 只重试上次失败的同一文件
uv run image-uploader --retry-failed --no-dry-run

# 指定报告路径
uv run image-uploader --report .import-runs/latest-report.csv
```

每次运行都会生成 CSV 报告，包含所有扫描到的图片。主要状态：

| 状态 | 含义 |
|------|------|
| `planned` | 已进入本次计划；dry-run 时不会上传 |
| `uploaded` | 已上传并确认入库 |
| `duplicate` | 后端判定为重复图片 |
| `failed` | 上传失败，可用 `--retry-failed` 重试 |
| `skipped_completed` | `--resume` 下跳过此前已完成文件 |
| `skipped_local_duplicate` | 本次扫描中 SHA256 重复，只保留第一次出现的文件上传 |

## 数据目录结构

图片需放在 `DATA_DIR` 下的编号文件夹中，工具会递归扫描每个编号文件夹：

```text
data/
├── 0/     # 无疵点
├── 1/     # 破洞
├── 2/     # 水渍 / 油渍 / 污渍
├── ...
└── 20/    # 死皱 / 云织 / 双纬 / 双经 / 跳纱 / 筘路 / 纬纱不良
```

支持的图片后缀：`.jpg`、`.jpeg`、`.png`、`.bmp`、`.gif`、`.webp`。

## 行为说明

- 分类直接复用项目种子数据中的 `纺织瑕疵`。
- 标签组直接复用项目种子数据中的 `瑕疵`。
- 老项目所有标签名保持不变。
- 若分类、标签组或任一标签缺失，脚本会提示缺失项并退出，不会自动创建或改名。
- 上传使用新项目的上传会话接口，重复图片会按后端规则计入重复数。
- 本地重复图片默认只报告并跳过，不会修改已有图片分类或标签。
- 本工具不直连数据库，不需要 `taxonomy:manage` 权限。
