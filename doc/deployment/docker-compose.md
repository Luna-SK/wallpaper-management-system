# Docker Compose 部署

## 服务

- `mysql`：MySQL 8.4 长期支持版本。
- `redis`：Redis 7，缓存和短期令牌辅助存储。
- `rustfs`：RustFS 1.0.0-alpha.98，S3 兼容对象存储。
- `backend`：Spring Boot 后端接口。
- `frontend`：Nginx 静态资源和接口反向代理。
- `backup`：按 Docker Compose 备份配置档手动执行数据库备份。

## 项目名与端口

新项目不复用旧项目 Docker Compose。统一使用 `wallpaper` 作为 Compose 项目名：

```bash
docker compose -p wallpaper --env-file .env up -d
```

默认端口映射：

- MySQL：`13316:3306`
- Redis：`16389:6379`
- RustFS 接口：`19010:9000`
- RustFS 控制台：`19011:9001`
- 后端接口：`18090:18090`
- 前端：`80:80`

## 常用命令

```bash
cd ops/docker
cp .env.example .env
docker compose -p wallpaper --env-file .env config
docker compose -p wallpaper --env-file .env up -d --build
docker compose -p wallpaper logs -f backend
```

## 环境文件

Docker Compose 只通过 `--env-file .env` 读取 `ops/docker/.env`。该文件属于 Docker 部署项目，和后端本地开发环境文件相互独立。

后端本地开发环境文件是 `backend/.env`；Docker Compose 不读取它。前端也不读取后端或 Docker 的环境文件。

项目中的真实 `.env` 文件都会被 Git 忽略，只提交 `.env.example` 模板。

后端根据 `DB_HOST`、`DB_PORT` 和 `DB_NAME` 拼接 JDBC 地址；Docker Compose 会把这些变量注入后端容器，不传递 `DB_URL`。

`SERVER_PORT` 控制后端容器内部监听端口，`BACKEND_PORT` 控制 Docker 暴露到宿主机的端口。二者默认都是 `18090`，前端 Nginx 容器会把 `/api` 代理到 `backend:18090`。

`APP_SECURITY_JWT_SECRET` 用于签发 JWT 访问令牌，每套部署都必须替换。访问令牌和刷新会话有效期由 `APP_SECURITY_ACCESS_TOKEN_TTL` 与 `APP_SECURITY_REFRESH_TOKEN_TTL` 控制。`APP_SECURITY_DEVELOPMENT_TOKEN_ENABLED` 默认为 `false`；除明确的本地诊断场景外，应保持关闭。

`UPLOAD_MAX_FILE_SIZE` 和 `UPLOAD_MAX_REQUEST_SIZE` 是文件上传启动硬上限。管理员可以在系统设置页面调低运行期业务上限，但不能超过 Docker `.env` 中配置的硬上限。

备份：

```bash
docker compose -p wallpaper --env-file .env --profile backup run --rm backup
```

执行数据库结构迁移前必须先保留一份可离线校验的 MySQL 备份。备份目录可按部署环境自行指定；示例命令默认使用仓库同级的 `../backup` 目录。备份文件需通过 `gzip -t` 和 `shasum -a 256` 校验后再继续迁移。

## 对象存储备份

图片对象和审计归档文件保存在 RustFS。当前图片上传会把原图、缩略图、标准预览图和高清预览图写入 `RUSTFS_BUCKET_ORIGINAL`；审计日志归档写入 `RUSTFS_BUCKET_AUDIT`。`RUSTFS_BUCKET_PREVIEW`、`RUSTFS_BUCKET_THUMBNAIL` 和 `RUSTFS_BUCKET_WATERMARK` 作为兼容和后续扩展存储桶，也应纳入备份范围，命令会自动跳过尚未创建的存储桶。

对象存储备份必须和 MySQL 备份成对保存。MySQL 中的 `image_versions`、`upload_batch_items` 和 `audit_log_archives` 记录的是存储桶与对象键；只恢复数据库或只恢复对象都会导致图片预览、下载或审计归档读取失败。执行迁移、批量清理、RustFS 升级或主机迁移前，先完成 MySQL 备份，再立即执行对象存储备份，并使用同一个时间戳目录保存。

### 备份

在 `ops/docker/` 下执行。命令通过 S3 兼容接口读取 RustFS，不直接读取 Docker 数据卷，适用于本地 Docker 部署和后续兼容 S3 存储：

```bash
cd ops/docker

backup_root=${BACKUP_ROOT:-../backup}
stamp=$(date +%Y%m%d%H%M%S)
object_backup_dir="$backup_root/rustfs-$stamp"
archive="$backup_root/rustfs-$stamp.tar.gz"

mkdir -p "$object_backup_dir/objects"
printf 'created_at=%s\ncompose_project=wallpaper\n' "$(date -Iseconds)" > "$object_backup_dir/backup-info.txt"

docker run --rm \
  --network wallpaper_default \
  --env-file .env \
  -v "$object_backup_dir:/backup" \
  --entrypoint /bin/sh \
  minio/mc -eu -c '
    mc alias set rustfs http://rustfs:9000 "$RUSTFS_ACCESS_KEY" "$RUSTFS_SECRET_KEY" >/dev/null
    for bucket in \
      "$RUSTFS_BUCKET_ORIGINAL" \
      "$RUSTFS_BUCKET_PREVIEW" \
      "$RUSTFS_BUCKET_THUMBNAIL" \
      "$RUSTFS_BUCKET_WATERMARK" \
      "$RUSTFS_BUCKET_AUDIT"
    do
      [ -n "$bucket" ] || continue
      if mc ls "rustfs/$bucket" >/dev/null 2>&1; then
        mkdir -p "/backup/objects/$bucket"
        mc mirror --overwrite --remove "rustfs/$bucket" "/backup/objects/$bucket"
        mc ls --recursive "rustfs/$bucket" > "/backup/manifest-$bucket.txt"
      else
        echo "skip missing bucket: $bucket" | tee -a /backup/backup-info.txt
      fi
    done
  '

tar -C "$backup_root" -czf "$archive" "rustfs-$stamp"
gzip -t "$archive"
shasum -a 256 "$archive" | tee "$archive.sha256"
```

备份产物包括：

- `rustfs-<timestamp>.tar.gz`：对象存储离线备份包。
- `rustfs-<timestamp>.tar.gz.sha256`：备份包 SHA-256 校验文件。
- `rustfs-<timestamp>/manifest-<bucket>.txt`：每个已存在存储桶的对象清单，便于恢复后抽查。

备份完成后至少保留 `tar.gz` 和 `.sha256` 文件。展开目录可按空间情况删除：

```bash
rm -rf "$object_backup_dir"
```

### 恢复演练

恢复演练建议在临时环境或空 RustFS 数据卷上进行，不要直接覆盖正在使用的生产存储桶。先恢复同一时间点的 MySQL 备份，再恢复对象存储备份。

```bash
cd ops/docker
set -a
. ./.env
set +a

backup_root=${BACKUP_ROOT:-../backup}
stamp=20260426163000
archive="$backup_root/rustfs-$stamp.tar.gz"
restore_dir="$backup_root/rustfs-$stamp"

shasum -a 256 -c "$archive.sha256"
gzip -t "$archive"
tar -C "$backup_root" -xzf "$archive"

docker compose -p wallpaper --env-file .env stop backend frontend

docker run --rm \
  --network wallpaper_default \
  --env-file .env \
  -v "$restore_dir:/backup" \
  --entrypoint /bin/sh \
  minio/mc -eu -c '
    mc alias set rustfs http://rustfs:9000 "$RUSTFS_ACCESS_KEY" "$RUSTFS_SECRET_KEY" >/dev/null
    for bucket in \
      "$RUSTFS_BUCKET_ORIGINAL" \
      "$RUSTFS_BUCKET_PREVIEW" \
      "$RUSTFS_BUCKET_THUMBNAIL" \
      "$RUSTFS_BUCKET_WATERMARK" \
      "$RUSTFS_BUCKET_AUDIT"
    do
      [ -d "/backup/objects/$bucket" ] || continue
      mc mb --ignore-existing "rustfs/$bucket"
      mc mirror --overwrite "/backup/objects/$bucket" "rustfs/$bucket"
      mc ls --recursive "rustfs/$bucket" > "/backup/restored-manifest-$bucket.txt"
    done
  '

docker compose -p wallpaper --env-file .env up -d backend frontend
```

如需把目标存储桶精确恢复为备份时状态，且已经确认目标环境可被覆盖，可把恢复命令中的 `mc mirror --overwrite` 改为 `mc mirror --overwrite --remove`。该选项会删除备份中不存在的远端对象，生产环境使用前必须先完成当前 MySQL 和 RustFS 备份。

恢复后检查：

```bash
diff -u "$restore_dir/manifest-$RUSTFS_BUCKET_ORIGINAL.txt" \
  "$restore_dir/restored-manifest-$RUSTFS_BUCKET_ORIGINAL.txt"

docker compose -p wallpaper --env-file .env logs --tail=120 backend
```

再登录前端抽查图片缩略图、预览、下载和审计归档列表。若后端日志出现 `NoSuchBucket`、`NoSuchKey` 或图片读取失败，优先核对 MySQL 备份和对象备份是否来自同一时间点。

### 执行频率与保留策略

- 开发环境：每次数据库迁移、批量清理或 RustFS 升级前手动执行一次 MySQL 备份和对象存储备份。
- 小规模部署：每天至少执行一次对象存储备份；保留最近 7 天每日备份、最近 4 周每周备份和最近 3 个月每月备份。
- 备份包应复制到 Docker 主机以外的位置，例如 NAS、另一台服务器或云对象存储。
- 每月至少做一次恢复演练，验证 MySQL 备份与 RustFS 备份可以一起恢复出可预览、可下载的图片库。

## 说明

- `ops/docker/.env.example` 只提供占位值，真实部署必须替换密钥。
- 依赖镜像使用本地已存在版本：`mysql:8.4`、`redis:7`、`rustfs/rustfs:1.0.0-alpha.98`。
- Nginx 和后端上传硬上限均按 500MB 批量请求设计；页面中的业务上限必须小于等于该硬上限。
- Docker 前端生产代理使用 `frontend/nginx.conf`，默认转发 `/api` 到 `backend:18090`。
- 数据库结构由后端启动时的 Liquibase 初始化和迁移完成；后端持久层使用 MyBatis-Plus，不启用运行期自动建表或改表。
- 审计日志历史归档写入 RustFS，默认存储桶为 `wallpaper-audit`，对象路径形如 `audit-logs/2026/04/audit-log-archive-20260424-023000.jsonl.gz`。
- 审计日志归档文件是 gzip JSONL，可下载后解压按行解析；只有写入成功的日志才会从 MySQL 清理。
- 已停用图片自动清理由系统设置控制，默认关闭；启用后按保留期和 cron 执行，默认每周日 03:00，并在后端启动后补偿检查一次。
