# wzut-wallpaper-manager

图片管理系统。

本项目是一个全新的独立实现，面向墙布图片、分类、标签组、标签、图片检索、访问控制和审计统计。纺织瑕疵图片管理是一期内置场景，后续可扩展到更多墙布图片分类和标签维度。

## Structure

```text
wzut-wallpaper-manager/
├── backend/             # Spring Boot 4 + Java 25 + MyBatis-Plus + Liquibase
├── frontend/            # Vue 3 + TypeScript + Vite
├── ops/docker/          # Docker Compose deployment
├── tools/image-importer/# Batch import tool
└── doc/                 # Planning, architecture, API, deployment, acceptance docs
```

## Project Handoff

交接或新建开发上下文时，请把本目录 `wzut-wallpaper-manager/` 作为项目根目录。

- 开发、验证和提交工作流：[doc/development/workflow.md](doc/development/workflow.md)

提交信息统一使用 Conventional Commits，例如：

```text
fix(taxonomy): hide disabled tags in image library
docs(workflow): add development handoff guide
```

## Quick Start

### One-Click Docker Deploy

本地演示：

```bash
sh scripts/deploy.sh --mode local
```

Windows PowerShell：

```powershell
.\scripts\deploy.ps1 -Mode local
```

Linux 云服务器开启 HTTPS：

```bash
sh scripts/deploy.sh --mode production --domain example.com --email admin@example.com --https
```

脚本会生成 `ops/docker/.env`、强随机密钥和初始 `admin` 密码，执行 Compose 配置校验、构建/拉取镜像、启动服务并等待健康检查。生产模式默认只公开 Web 入口，不公开 MySQL、Redis、RustFS 或后端调试端口；邮件找回密码默认关闭，需要配置 SMTP 后由管理员在系统设置中开启。完整说明见 [doc/deployment/one-click-deploy.md](doc/deployment/one-click-deploy.md)。

Backend local configuration is independent from the frontend and Docker Compose configuration.
The backend reads only `backend/.env` when it is started from the `backend/` directory.
Create the local file from the committed template. The template keeps the original local defaults that previously lived in `application.yml`; `DB_URL` is not written to `.env`, because Spring builds it from `DB_HOST`, `DB_PORT`, and `DB_NAME`.

```bash
cd backend
cp .env.example .env
```

Backend:

```bash
cd backend
JAVA_HOME=/Users/luna/.sdkman/candidates/java/25.0.1-zulu ./mvnw test
./mvnw spring-boot:run
```

The default local backend port is `18090`, read from `backend/.env`.

Frontend:

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

The frontend dev server keeps API calls at `/api`; Vite reads `frontend/.env` and proxies them to `http://localhost:18090` by default.

Docker config check:

```bash
cd ops/docker
docker compose -f compose.yaml -f compose.build.yaml -f compose.local.yaml --env-file .env.example config
```

## Configuration Boundaries

- Database schema changes are managed only through Liquibase changelogs. The backend uses MyBatis-Plus for persistence and does not rely on runtime ORM DDL generation.
- `backend/.env` is the backend local development configuration file. It is ignored by Git; commit changes only to `backend/.env.example`.
- `frontend/.env` is the frontend local development configuration file. It is ignored by Git; commit changes only to `frontend/.env.example`.
- `ops/docker/.env` is the Docker Compose deployment configuration file. It is ignored by Git; commit changes only to `ops/docker/.env.example`.
- Real `.env` files anywhere in the project are ignored by Git. Keep only `.env.example` templates under version control.
- The backend local port comes from `backend/.env`, the frontend dev proxy port comes from `frontend/.env`, and Docker deployment ports come from `ops/docker/.env`.
- The frontend keeps its own Vite/Nginx configuration and does not read backend or Docker `.env` files.
- Do not copy one environment file over another. The three projects stay independent even when some variable names are intentionally similar.
- `DB_NAME` is the database name. `DB_USERNAME` is the database login user.
- `APP_SECURITY_JWT_SECRET` signs access tokens and must be replaced outside local development; `APP_SECURITY_ACCESS_TOKEN_TTL` and `APP_SECURITY_REFRESH_TOKEN_TTL` control access-token and refresh-session lifetime.
- `APP_SECURITY_DEVELOPMENT_TOKEN_ENABLED` defaults to `false`; only enable it explicitly for local/test bypass scenarios.
- `UPLOAD_MAX_FILE_SIZE` and `UPLOAD_MAX_REQUEST_SIZE` are backend startup hard limits. The values on the System Settings page are runtime business limits and cannot be set above these hard limits.
- Soft-deleted image cleanup is controlled from System Settings. The cleanup switch is off by default, the retention period is configurable, and the cleanup schedule uses a Spring cron expression. The default schedule is every Sunday at 03:00: `0 0 3 * * SUN`.
- Database schema changes must go through Liquibase. The persistence layer uses MyBatis-Plus; do not reintroduce Spring Data JPA or Hibernate runtime DDL behavior.
