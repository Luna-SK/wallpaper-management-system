# wzut-wallpaper-manager

图片管理系统。

本项目主要用于配合深度学习墙布图片瑕疵检测项目，管理检测数据、检测结果、人工复核、分类标签、权限审计和用户反馈闭环；同时它不绑定单一检测模型，也可作为泛用图片管理系统使用。

本项目是一个全新的独立实现，面向墙布图片、分类、标签组、标签、图片检索、访问控制和审计统计。纺织瑕疵图片管理是一期内置场景，后续可扩展到更多墙布图片分类和标签维度。

## 项目结构

```text
wzut-wallpaper-manager/
├── backend/             # Spring Boot 4 + Java 25 + MyBatis-Plus + Liquibase
├── frontend/            # Vue 3 + TypeScript + Vite 前端
├── image-uploader/      # 当前正式图片批量导入工具
├── ops/docker/          # Docker Compose 部署配置
└── doc/                 # 规划、架构、API、部署和验收文档
```

## 项目交接

交接或新建开发上下文时，请把本目录 `wzut-wallpaper-manager/` 作为项目根目录。

- 开发、验证和提交工作流：[doc/development/workflow.md](doc/development/workflow.md)

提交信息统一使用 Conventional Commits，例如：

```text
fix(taxonomy): hide disabled tags in image library
docs(workflow): add development handoff guide
```

## 运行环境与配置要求

Docker 一键部署最低依赖：

- Docker Desktop，或 Linux 服务器上的 Docker Engine。
- Docker Compose v2，即 `docker compose version` 可用。
- Windows 需要 PowerShell 5.1+；macOS / Linux 需要 `sh` 和基础 Unix 工具。

Docker 部署不要求宿主机安装 Java、Node.js、Maven 或 npm；默认构建发生在 Docker 镜像内部，使用预构建镜像时只需要 Docker 拉取并启动镜像。

本地开发依赖：

- 后端：Java 25；Maven 使用仓库内 Maven Wrapper。
- 前端：Node.js 25、npm。
- 图片导入工具：Python 3.11+、uv。

演示部署的最低和推荐规格、生产端口暴露策略、预构建镜像建议见 [doc/deployment/one-click-deploy.md](doc/deployment/one-click-deploy.md)。Docker Compose 服务版本与运行时说明见 [doc/deployment/docker-compose.md](doc/deployment/docker-compose.md)。

## 快速开始

### 一键 Docker 部署

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

后端本地配置独立于前端和 Docker Compose 配置。从 `backend/` 目录启动后端时，只读取 `backend/.env`。本地文件请从已提交模板复制生成；模板保留原本在 `application.yml` 中的本地默认值。`.env` 中不写 `DB_URL`，因为 Spring 会通过 `DB_HOST`、`DB_PORT` 和 `DB_NAME` 拼接 JDBC URL。

```bash
cd backend
cp .env.example .env
```

后端：

```bash
cd backend
JAVA_HOME=/Users/luna/.sdkman/candidates/java/25.0.1-zulu ./mvnw test
./mvnw spring-boot:run
```

后端本地默认端口为 `18090`，从 `backend/.env` 读取。

前端：

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

前端开发服务器保持 API 请求路径为 `/api`；Vite 读取 `frontend/.env`，默认代理到 `http://localhost:18090`。

Docker 配置检查：

```bash
cd ops/docker
docker compose -f compose.yaml -f compose.build.yaml -f compose.local.yaml --env-file .env.example config
```

图片导入工具：

```bash
cd image-uploader
cp .env.example .env
uv sync
uv run image-uploader
```

## 配置边界

- 数据库结构变更只通过 Liquibase changelog 管理。后端使用 MyBatis-Plus 持久化，不依赖运行时 ORM DDL 生成。
- `backend/.env` 是后端本地开发配置文件，会被 Git 忽略；需要提交的模板只改 `backend/.env.example`。
- `frontend/.env` 是前端本地开发配置文件，会被 Git 忽略；需要提交的模板只改 `frontend/.env.example`。
- `ops/docker/.env` 是 Docker Compose 部署配置文件，会被 Git 忽略；需要提交的模板只改 `ops/docker/.env.example`。
- 项目内真实 `.env` 文件都被 Git 忽略，版本库中只保留 `.env.example` 模板。
- 后端本地端口来自 `backend/.env`，前端开发代理端口来自 `frontend/.env`，Docker 部署端口来自 `ops/docker/.env`。
- 前端维护自己的 Vite/Nginx 配置，不读取后端或 Docker 的 `.env` 文件。
- 不要把一个环境文件复制覆盖到另一个项目中。即使部分变量名相似，后端、前端和 Docker 部署配置也应保持独立。
- `DB_NAME` 是数据库名，`DB_USERNAME` 是数据库登录用户。
- `APP_SECURITY_JWT_SECRET` 用于签发 access token，非本地开发环境必须替换；`APP_SECURITY_ACCESS_TOKEN_TTL` 和 `APP_SECURITY_REFRESH_TOKEN_TTL` 控制 access token 与 refresh session 生命周期。
- `APP_SECURITY_DEVELOPMENT_TOKEN_ENABLED` 默认是 `false`；仅在明确的本地或测试旁路场景中开启。
- `UPLOAD_MAX_FILE_SIZE` 和 `UPLOAD_MAX_REQUEST_SIZE` 是后端启动时的硬上限。系统设置页中的上传限制是运行期业务上限，不能超过这些硬上限。
- 已停用图片清理由系统设置控制。清理开关默认关闭，保留期可配置，执行计划使用 Spring cron 表达式；默认计划为每周日 03:00：`0 0 3 * * SUN`。
- 数据库结构变更必须经过 Liquibase。持久层使用 MyBatis-Plus，不要重新引入 Spring Data JPA 或 Hibernate 运行时 DDL 行为。
