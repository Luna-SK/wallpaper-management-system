# Docker Compose 部署

## 服务

- `mysql`：MySQL 8.4 长期支持版本。
- `redis`：Redis 7，缓存和短期令牌辅助存储。
- `rustfs`：RustFS 1.0.0-alpha.98，S3 兼容对象存储。
- `rustfs-init`：一次性初始化 RustFS 存储桶，后端等待该任务完成后启动。
- `mailpit`：本地邮件捕获服务，用于验证找回密码邮件，只在 `compose.local.yaml` 且启用 `local-mail` profile 时启动。
- `backend`：Spring Boot 后端接口。
- `frontend`：Nginx 静态资源和接口反向代理。
- `caddy`：可选 HTTPS 反向代理，只在启用 `compose.https.yaml` 时启动。
- `backup`：按 Docker Compose 备份配置档手动执行数据库备份。

## Compose 文件与端口

新项目不复用旧项目 Docker Compose。统一使用 `wallpaper` 作为 Compose 项目名：

```bash
docker compose -p wallpaper --env-file .env -f compose.yaml -f compose.build.yaml -f compose.local.yaml up -d --build
```

部署文件分层：

- `compose.yaml`：生产安全基础配置，不发布 MySQL、Redis、RustFS、backend 或 frontend 端口。
- `compose.build.yaml`：使用本仓库 Dockerfile 本地构建 backend/frontend。
- `compose.local.yaml`：本地演示和调试端口发布。
- `compose.web.yaml`：生产 HTTP 入口，只发布前端 Web 端口。
- `compose.https.yaml`：Caddy HTTPS 入口，发布 `80/443`。
- `compose.release.yaml`：只拉取预构建 backend/frontend 镜像，配合 GHCR/Docker Hub 发布使用。

本地模式默认端口映射：

- MySQL：`13316:3306`
- Redis：`16389:6379`
- RustFS 接口：`19010:9000`
- RustFS 控制台：`19011:9001`
- Mailpit SMTP：`1025:1025`（启用 `local-mail` profile 时）
- Mailpit 收件箱：`8025:8025`（启用 `local-mail` profile 时）
- 后端接口：`18090:18090`
- 前端：`80:80`

生产 HTTP/HTTPS 模式只公开 Web 入口；数据库、缓存、对象存储和后端调试端口只在 Docker 内部网络可见。

## 常用命令

推荐使用仓库根目录的一键部署脚本：

```bash
sh scripts/deploy.sh --mode local
sh scripts/deploy.sh --mode production --domain example.com --email admin@example.com --https
```

Windows PowerShell：

```powershell
.\scripts\deploy.ps1 -Mode local
.\scripts\deploy.ps1 -Mode production -Domain example.com -Email admin@example.com -Https
```

手动 Compose 命令：

```bash
cd ops/docker
cp .env.example .env
docker compose -p wallpaper --env-file .env -f compose.yaml -f compose.build.yaml -f compose.local.yaml config
docker compose -p wallpaper --env-file .env -f compose.yaml -f compose.build.yaml -f compose.local.yaml up -d --build
docker compose -p wallpaper logs -f backend
```

## 代码变更后的运行态刷新

使用 Docker Compose 访问 `http://localhost` 时，前端静态资源来自 `frontend` 镜像，后端接口来自 `backend` 镜像。代码变更后通常需要重建对应服务，浏览器刷新本身不会让容器使用新的源码。

前端代码或 Nginx 配置变更：

```bash
cd ops/docker
docker compose -p wallpaper --env-file .env -f compose.yaml -f compose.build.yaml -f compose.local.yaml up -d --build frontend
```

后端 Java 代码、配置或 Liquibase changelog 变更：

```bash
cd ops/docker
docker compose -p wallpaper --env-file .env -f compose.yaml -f compose.build.yaml -f compose.local.yaml up -d --build backend
```

前后端同时变更：

```bash
cd ops/docker
docker compose -p wallpaper --env-file .env -f compose.yaml -f compose.build.yaml -f compose.local.yaml up -d --build backend frontend
```

如果变更会应用到 Docker MySQL 的 schema 或种子数据，先完成 MySQL 备份并通过 `gzip -t` 和 `shasum -a 256` 校验，再重建并启动后端。只调整 `.env` 时通常执行 `docker compose -p wallpaper --env-file .env up -d <service>` 即可；如果变更同时依赖镜像构建产物，再加 `--build`。

## 环境文件

Docker Compose 只通过 `--env-file .env` 读取 `ops/docker/.env`。该文件属于 Docker 部署项目，和后端本地开发环境文件相互独立。

后端本地开发环境文件是 `backend/.env`；Docker Compose 不读取它。前端也不读取后端或 Docker 的环境文件。

项目中的真实 `.env` 文件都会被 Git 忽略，只提交 `.env.example` 模板。

后端根据 `DB_HOST`、`DB_PORT` 和 `DB_NAME` 拼接 JDBC 地址；Docker Compose 会把这些变量注入后端容器，不传递 `DB_URL`。

Docker 后端容器内部固定监听 `18090`，前端 Nginx 容器会把 `/api` 代理到 `backend:18090`。`BACKEND_PORT` 只在 `compose.local.yaml` 中用于把后端调试端口发布到宿主机；生产部署默认不发布后端端口。

`APP_BOOTSTRAP_ADMIN_PASSWORD` 是首次启动时应用到内置 `admin` 用户的一次性初始密码。成功应用后会在 `system_settings` 中记录状态，后续修改该环境变量不会反复覆盖管理员密码。`APP_BOOTSTRAP_DEMO_USERS_ENABLED=false` 时，首次启动会禁用内置 `manager`、`editor`、`viewer` 演示账号；一键部署的生产模式默认关闭这些演示账号。

`APP_SECURITY_JWT_SECRET` 用于签发 JWT 访问令牌，每套部署都必须替换。访问令牌和刷新会话有效期由 `APP_SECURITY_ACCESS_TOKEN_TTL` 与 `APP_SECURITY_REFRESH_TOKEN_TTL` 控制。`APP_SECURITY_DEVELOPMENT_TOKEN_ENABLED` 默认为 `false`；除明确的本地诊断场景外，应保持关闭。

邮件找回密码由系统设置业务开关和 `APP_MAIL_*` 环境变量共同控制。系统设置中的“邮件找回密码”默认关闭；关闭后登录页不再提供找回入口，公开申请和确认接口也不可用；`APP_MAIL_ENABLED` 和其他 `APP_MAIL_*` 只控制 SMTP 传输层。`APP_MAIL_FRONTEND_BASE_URL` 用于生成重置链接，本地 Compose 默认填写 `http://localhost`；`APP_MAIL_PASSWORD_RESET_TOKEN_TTL` 默认 `30m`。

生产部署不能使用 Mailpit 作为正式投递服务。启用邮件找回密码前，应先将 `APP_MAIL_ENABLED`、`APP_MAIL_HOST`、`APP_MAIL_PORT`、`APP_MAIL_USERNAME`、`APP_MAIL_PASSWORD`、`APP_MAIL_FROM`、`APP_MAIL_SMTP_AUTH`、`APP_MAIL_SMTP_STARTTLS` 和 `APP_MAIL_SMTP_SSL` 切换为企业邮箱或云邮件服务 SMTP 配置，并将 `APP_MAIL_FRONTEND_BASE_URL` 改为用户实际访问的前端地址；确认 SMTP 配置可用后，再由管理员在系统设置中开启“邮件找回密码”。STARTTLS 端口通常配置 `APP_MAIL_SMTP_STARTTLS=true`、`APP_MAIL_SMTP_SSL=false`；465 SSL 端口通常配置 `APP_MAIL_SMTP_SSL=true`、`APP_MAIL_SMTP_STARTTLS=false`。生产发信域名建议配置 SPF、DKIM 和 DMARC，降低找回密码邮件进入垃圾箱的概率。

本地验证找回密码邮件：

```bash
cd ops/docker
APP_MAIL_ENABLED=true APP_MAIL_HOST=mailpit APP_MAIL_PORT=1025 docker compose -p wallpaper --env-file .env -f compose.yaml -f compose.build.yaml -f compose.local.yaml --profile local-mail up -d --build
```

启动后访问前端 `http://localhost`，由管理员在系统设置中开启“邮件找回密码”，再在登录页点击“忘记密码”，输入系统中已存在且已启用用户的唯一邮箱。随后打开 `http://localhost:8025`，在 Mailpit 收件箱中查看密码重置邮件，点击邮件中的链接完成密码重置。Mailpit 不需要真实发件邮箱或收件邮箱，用户邮箱字段只需要在系统账号中存在即可。若 SMTP 配置错误或邮件服务不可用，申请接口会返回“邮件服务暂不可用，请联系管理员”，项目不会因此启动失败。

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
- 一键部署脚本会自动生成真实 `ops/docker/.env`，并拒绝继续使用包含 `change-me` 占位符的现有 `.env`。
- 依赖镜像使用 `mysql:8.4`、`redis:7`、`rustfs/rustfs:1.0.0-alpha.98`、`minio/mc`，HTTPS 模式额外使用 `caddy:2-alpine`。
- Nginx 和后端上传硬上限均按 500MB 批量请求设计；页面中的业务上限必须小于等于该硬上限。
- Docker 前端生产代理使用 `frontend/nginx.conf`，默认转发 `/api` 到 `backend:18090`。
- 数据库结构由后端启动时的 Liquibase 初始化和迁移完成；后端持久层使用 MyBatis-Plus，不启用运行期自动建表或改表。
- 审计日志历史归档写入 RustFS，默认存储桶为 `wallpaper-audit`，对象路径形如 `audit-logs/2026/04/audit-log-archive-20260424-023000.jsonl.gz`。
- 审计日志归档文件是 gzip JSONL，可下载后解压按行解析；只有写入成功的日志才会从 MySQL 清理。
- 已停用图片自动清理由系统设置控制，默认关闭；启用后按保留期和 cron 执行，默认每周日 03:00，并在后端启动后补偿检查一次。
