# wzut-wallpaper-manager

图片管理系统。

本项目是一个全新的独立实现，面向墙布图片、分类标签、图片检索、访问控制和审计统计。纺织瑕疵图片管理是一期内置场景，后续可扩展到更多墙布图片分类。

## Structure

```text
wzut-wallpaper-manager/
├── backend/             # Spring Boot 4 + Java 25
├── frontend/            # Vue 3 + TypeScript + Vite
├── ops/docker/          # Docker Compose deployment
├── tools/image-importer/# Batch import tool
└── doc/                 # Planning, architecture, API, deployment, acceptance docs
```

## Quick Start

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
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Docker config check:

```bash
cd ops/docker
docker compose --env-file .env.example config
```

## Configuration Boundaries

- `backend/.env` is the backend local development configuration file. It is ignored by Git; commit changes only to `backend/.env.example`.
- `ops/docker/.env` is the Docker Compose deployment configuration file. It is ignored by Git; commit changes only to `ops/docker/.env.example`.
- Real `.env` files anywhere in the project are ignored by Git. Keep only `.env.example` templates under version control.
- The frontend keeps its own Vite/Nginx configuration and does not read backend or Docker `.env` files.
- Do not copy one environment file over another. The three projects stay independent even when some variable names are intentionally similar.
- `DB_NAME` is the database name. `DB_USERNAME` is the database login user.
- `UPLOAD_MAX_FILE_SIZE` and `UPLOAD_MAX_REQUEST_SIZE` are backend startup hard limits. The values on the System Settings page are runtime business limits and cannot be set above these hard limits.
- Soft-deleted image cleanup is controlled from System Settings. The cleanup switch is off by default, the retention period is configurable, and the cleanup schedule uses a Spring cron expression. The default schedule is every Sunday at 03:00: `0 0 3 * * SUN`.
