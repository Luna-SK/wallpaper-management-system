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
