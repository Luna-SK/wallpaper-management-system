# Image Uploader

面向新项目的图片导入工具。它复用老项目 `0` 到 `20` 文件夹与标签映射，但只调用新系统 API，不直连数据库。

## 前置条件

- Python 3.11+
- [uv](https://docs.astral.sh/uv/)
- 已启动的新项目后端服务
- 账号至少需要 `image:upload` 权限，并能访问分类与标签列表接口

大批量上传到远程或低配服务器时，建议先保持 `DRY_RUN=true` 验证分类、标签和报告，再真实上传。服务器内存或 I/O 较弱时，把 `BATCH_SIZE` 调小到 `5-10`，并把 `REQUEST_TIMEOUT_SECONDS` 调大到 `300` 或更高，避免单个上传会话处理过久触发代理或客户端超时。

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

## 多环境配置文件

默认情况下，CLI 会读取当前目录下的 `.env`。如果需要同时维护本地、测试、云服务器等多套配置，可以复制 `.env.example` 为多个私有配置文件，并在运行时显式指定：

```bash
uv run image-uploader --env-file <配置文件路径>
uv run image-uploader --env-file <配置文件路径> --no-dry-run
```

命令行参数仍然优先于配置文件，例如：

```bash
uv run image-uploader --env-file <配置文件路径> --no-dry-run --skip-completed
uv run image-uploader --env-file <配置文件路径> --retry-failed
```

推荐使用 `--env-file` 读取多环境配置，不推荐用 `set -a; . <file>; set +a` 把配置导入 shell 环境。部分 shell 自带特殊变量名，可能和配置项同名并覆盖文件内容，排查起来很绕。

## 配置

| 变量 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `API_BASE_URL` | 可选 | `http://localhost:18090/api` | 后端 API 地址。本机 Docker 或本地后端通常保留默认；上传到远程环境时改成远程后端的 `/api` 地址 |
| `USERNAME` / `PASSWORD` | 条件必填 | 空 | 未配置 `AUTHORIZATION_HEADER` 时必须填写。推荐使用管理员账号；本机 Docker 的 `admin` 密码来自部署脚本输出，使用用户名密码登录时脚本可自动 refresh token |
| `AUTHORIZATION_HEADER` | 条件必填 | 空 | 不使用用户名密码登录时填写完整 HTTP `Authorization` 请求头，例如 `Bearer eyJ...`，不是只填 token；仅建议高级或临时调试场景使用 |
| `DATA_DIR` | 必填 | 无 | 图片数据目录，建议填绝对路径；目录下必须包含 `0` 到 `20` 子目录，脚本会递归扫描这些编号目录 |
| `CATEGORY_CODE` / `CATEGORY_NAME` | 可选 | `TEXTILE_DEFECT / 纺织瑕疵` | 要复用的图片分类。脚本只校验并使用已有分类，不会自动创建或改名 |
| `TAG_GROUP_CODE` / `TAG_GROUP_NAME` | 可选 | `DEFECT / 瑕疵` | 要复用的标签组。脚本只校验并使用已有标签组及其标签，不会自动创建或改名 |
| `DRY_RUN` | 可选 | `true` | `true` 只预览、校验并生成报告，不上传；首次运行建议保持 `true`，确认报告后再改为 `false` |
| `BATCH_SIZE` | 可选 | `50` | 每个上传会话包含的最大图片数，必须大于 0；普通本机和 Docker 环境保留默认即可，调太大可能增加单次会话压力 |
| `REQUEST_TIMEOUT_SECONDS` | 可选 | `120` | 后端 API 请求超时时间，单位为秒；网络慢、图片较大或远程上传时可适当调大 |
| `SKIP_COMPLETED` | 可选 | `true` | 跳过已完成开关。默认适合断点续传和重复执行同一批导入，会跳过本地 checkpoint 中已成功上传或已确认重复且文件未变化的图片；如果后端数据库已清空或需要完整重传，设为 `false` 或删除对应 `.import-runs` 状态文件 |
| `RETRY_FAILED` | 可选 | `false` | 失败重试开关。设为 `true` 时只处理上次失败或中断且文件未变化的图片，适合修复配置、网络或后端问题后重跑 |
| `RUN_DIR` | 可选 | `.import-runs` | 本地 checkpoint 和自动报告目录，属于运行产物；通常保留默认，不要提交到 Git |
| `REPORT_FILE` | 可选 | 空 | 指定本次 CSV 报告输出路径；留空时在 `RUN_DIR` 中自动生成 `report-<timestamp>.csv` |

`USERNAME/PASSWORD` 和 `AUTHORIZATION_HEADER` 二选一即可；推荐使用 `USERNAME/PASSWORD`，因为脚本可以自动 refresh token。

本机 Docker 推荐只填写 `USERNAME=admin` 和一键部署脚本输出的 `admin` 密码，不需要手动复制令牌。若确需手动认证，请填写完整的 `AUTHORIZATION_HEADER`。

大量上传时，脚本使用 `USERNAME/PASSWORD` 登录后会在访问令牌过期时自动调用 `/api/auth/refresh` 续期，并重试一次原请求。手动 `AUTHORIZATION_HEADER` 无法自动续期，过期后需要重新填写。

脚本会在导入前读取系统的“上传去重”开关，并以 `后端上传去重（系统设置）` 显示当前状态；该开关只由后端执行，CLI 不提供本地图片内容去重开关。所有未被 `SKIP_COMPLETED` 或 `RETRY_FAILED` 过滤的文件都会提交给后端上传会话，由后端根据系统设置决定入库或判重。

如果刚清空后端数据并准备完整重传，记得把 `SKIP_COMPLETED=false`，或删除 `RUN_DIR` 中对应数据目录的 `state-*.jsonl`。否则脚本会按本地 checkpoint 跳过已完成记录。

如果使用 `--env-file` 指向本机私有配置文件，文件中的值会覆盖 README 中的默认值。例如配置文件里写了 `SKIP_COMPLETED=false`，运行时就会显示 `跳过已完成（本地 checkpoint）: 关闭`。

## 跳过已完成与失败重试

真实上传时，工具会把每个文件的最终状态追加写入本地 JSONL checkpoint：

```text
.import-runs/state-<data_dir_hash>.jsonl
```

状态匹配使用 `relativePath + sizeBytes + mtimeNs + sha256`，文件内容或时间变化后不会被误跳过。

常用命令：

```bash
# 跳过此前已成功上传或已确认重复的同一文件
uv run image-uploader --skip-completed --no-dry-run

# 完整重传当前扫描到的文件，不跳过本地已完成记录
uv run image-uploader --no-skip-completed --no-dry-run

# 只重试上次失败或中断的同一文件
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
| `interrupted` | 用户按下 `Ctrl+C`，当前批次已取消或状态已记录 |
| `skipped_completed` | `SKIP_COMPLETED=true` 或 `--skip-completed` 下跳过此前已完成文件 |
| `skipped_not_retryable` | `--retry-failed` 下跳过没有失败/中断 checkpoint、checkpoint 已变化或状态不可重试的文件 |

报告会包含 `desiredTitle` 列。脚本会按标签生成友好标题，并在上传文件时直接提交给后端，例如 `破洞001`、`破洞002`，多个标签会生成 `水渍-油渍-污渍001`。编号按相同标签组合独立递增，超过 `999` 后会自然扩展为 `1000`、`1001`。

真实上传时，标题会在确认入库时一次性写入图片记录；脚本不再读取图片详情或调用图片编辑接口补标题。推荐在清空旧数据后重新导入，避免重复图片继承旧标题。

## 手动中断

按下 `Ctrl+C` 时，脚本会尽量优雅退出：

- 尚未确认的上传会话会调用取消接口，清理暂存对象。
- 如果中断发生在确认请求附近，脚本会先查询会话状态；已确认则记录后端结果，未确认则取消。
- 已确认入库的图片不会回滚；下次默认会跳过已完成项，也可使用 `--skip-completed --no-dry-run` 明确继续导入未完成项。
- 使用 `--retry-failed` 时，只有本地 checkpoint 中 `failed` 或 `interrupted` 且文件未变化的记录会进入上传队列；其它文件会在报告中标记为 `skipped_not_retryable`。
- 中断后仍会写入 checkpoint 和 CSV 报告，终端不会输出 Python traceback，退出码为 `130`。

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
- 上传使用新项目的上传会话接口，标题随每个上传 item 一起提交；是否去重由系统设置中的“上传去重”开关控制。
- 脚本不做本地图片判重；同一批次中内容相同的文件也会提交给后端，由后端上传去重开关决定如何处理。
- 本工具不直连数据库，不需要 `taxonomy:manage` 权限。
