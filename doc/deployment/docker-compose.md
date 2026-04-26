# Docker Compose 部署

## Services

- `mysql`：MySQL 8.4 LTS。
- `redis`：Redis 7，缓存和短期令牌辅助存储。
- `rustfs`：RustFS 1.0.0-alpha.98，S3 兼容对象存储。
- `backend`：Spring Boot API。
- `frontend`：Nginx 静态资源和 API 反向代理。
- `backup`：按 profile 手动执行数据库备份。

## Project Name And Ports

新项目不复用旧项目 Docker Compose。统一使用 `wallpaper` 作为 Compose project 名：

```bash
docker compose -p wallpaper --env-file .env up -d
```

默认端口映射：

- MySQL：`13316:3306`
- Redis：`16389:6379`
- RustFS API：`19010:9000`
- RustFS Console：`19011:9001`
- Backend API：`18090:18090`
- Frontend：`80:80`

## Commands

```bash
cd ops/docker
cp .env.example .env
docker compose -p wallpaper --env-file .env config
docker compose -p wallpaper --env-file .env up -d --build
docker compose -p wallpaper logs -f backend
```

## Environment Files

Docker Compose only reads `ops/docker/.env` through `--env-file .env`. This file belongs to the Docker deployment project and is independent from backend local development.

The backend local development file is `backend/.env`; Docker Compose does not read it. The frontend also does not read either backend or Docker environment files.

All real `.env` files in the project are ignored by Git. Commit only `.env.example` templates.

The backend builds its JDBC URL from `DB_HOST`, `DB_PORT`, and `DB_NAME`; Docker Compose injects those variables into the backend container instead of passing a `DB_URL`.

`SERVER_PORT` controls the backend container listening port, and `BACKEND_PORT` controls the host port exposed by Docker. The default for both is `18090`, and the frontend Nginx container proxies `/api` to `backend:18090`.

`APP_SECURITY_JWT_SECRET` signs JWT access tokens and must be changed for every deployment. Access token and refresh session lifetime are controlled by `APP_SECURITY_ACCESS_TOKEN_TTL` and `APP_SECURITY_REFRESH_TOKEN_TTL`. `APP_SECURITY_DEVELOPMENT_TOKEN_ENABLED` defaults to `false`; keep it disabled outside explicit local diagnostics.

`UPLOAD_MAX_FILE_SIZE` and `UPLOAD_MAX_REQUEST_SIZE` are startup hard limits for file upload. Administrators can lower runtime business limits on the System Settings page, but they cannot raise them above the Docker `.env` hard limits.

备份：

```bash
docker compose -p wallpaper --env-file .env --profile backup run --rm backup
```

执行数据库结构迁移前必须先保留一份可离线校验的 MySQL 备份。迁移演练中使用的备份目录为 `/Users/luna/Documents/work/submit/backup`，备份文件需通过 `gzip -t` 和 `shasum -a 256` 校验后再继续迁移。

## Notes

- `ops/docker/.env.example` 只提供占位值，真实部署必须替换密钥。
- 依赖镜像使用本地已存在版本：`mysql:8.4`、`redis:7`、`rustfs/rustfs:1.0.0-alpha.98`。
- Nginx 和后端上传硬上限均按 500MB 批量请求设计；页面中的业务上限必须小于等于该硬上限。
- Docker 前端生产代理使用 `frontend/nginx.conf`，默认转发 `/api` 到 `backend:18090`。
- 数据库结构由后端启动时的 Liquibase 初始化和迁移完成；后端持久层使用 MyBatis-Plus，不启用运行期自动建表或改表。
- 审计日志历史归档写入 RustFS，默认 bucket 为 `wallpaper-audit`，对象路径形如 `audit-logs/2026/04/audit-log-archive-20260424-023000.jsonl.gz`。
- 审计日志归档文件是 gzip JSONL，可下载后解压按行解析；只有写入成功的日志才会从 MySQL 清理。
- 已停用图片自动清理由系统设置控制，默认关闭；启用后按保留期和 cron 执行，默认每周日 03:00，并在后端启动后补偿检查一次。
