# 开发工作流

本文件记录新项目 `wzut-wallpaper-manager` 的日常开发、验证和提交约定。新建开发上下文时，请把仓库根目录作为项目根目录，并按本文件执行具体命令。

## 工作目录

所有新项目工作都在仓库根目录执行：

```bash
cd /Users/luna/Documents/work/submit/image-manage-system-pro/wzut-wallpaper-manager
```

Git 远程：

```bash
git@github.com:Luna-SK/wallpaper-management-system.git
```

不要在父目录旧项目中提交新项目改动。

## 技术栈

- 后端：Java 25、Spring Boot 4.0.x、Maven、MyBatis-Plus 3.5.16、Liquibase。
- 前端：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus。
- 服务：MySQL 8.4、Redis 7、RustFS。
- 导入工具：Python 3.11+、uv。

## 本地启动

后端：

```bash
cd backend
cp .env.example .env
./mvnw spring-boot:run
```

前端：

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

默认端口：

- 后端：`http://localhost:18090`
- 前端：`http://localhost:5173`
- MySQL：`localhost:13316`
- Redis：`localhost:16389`
- RustFS API：`http://localhost:19010`
- RustFS Console：`http://localhost:19011`

## Docker

```bash
cd ops/docker
cp .env.example .env
docker compose -p wallpaper --env-file .env -f compose.yaml -f compose.build.yaml -f compose.local.yaml config
docker compose -p wallpaper --env-file .env -f compose.yaml -f compose.build.yaml -f compose.local.yaml up -d --build
docker compose -p wallpaper --env-file .env logs -f backend
```

## 代码变更后的运行态刷新

本项目开发时需要先确认当前访问的是哪套运行态：

- 访问 `http://localhost:5173` 时，前端由 Vite dev server 提供。前端代码通常会通过 HMR 自动刷新；如果 HMR 未生效，重启 `frontend` 下的 `npm run dev`。后端 Java 代码、配置或 Liquibase 变更仍需要重启 `backend` 下的 `./mvnw spring-boot:run`。
- 访问 `http://localhost` 时，页面来自 Docker `frontend` 镜像。前端代码变更后需要重建并重启前端容器：

```bash
cd ops/docker
docker compose -p wallpaper --env-file .env -f compose.yaml -f compose.build.yaml -f compose.local.yaml up -d --build frontend
```

后端代码、配置或 Liquibase 变更后需要重建并重启后端容器：

```bash
cd ops/docker
docker compose -p wallpaper --env-file .env -f compose.yaml -f compose.build.yaml -f compose.local.yaml up -d --build backend
```

如果前后端都改了，可以一次性刷新：

```bash
cd ops/docker
docker compose -p wallpaper --env-file .env -f compose.yaml -f compose.build.yaml -f compose.local.yaml up -d --build backend frontend
```

涉及 Docker MySQL 的 schema 或数据迁移时，先按本文件“数据库规则”完成备份和校验，再启动后端应用 Liquibase 迁移。只修改文档、测试或未被运行态读取的文件时，不需要重建镜像。

## 数据库规则

- Liquibase 是数据库结构唯一真相源。
- 不启用 Hibernate、MyBatis-Plus 或其它运行期自动建表改表能力。
- 不重新加入 Spring Data JPA / Hibernate。
- 增量结构变更优先新增 changelog，并同步更新数据模型、API、部署或验收文档。
- 修改 Docker MySQL 结构或批量清理数据前，必须先备份并校验：

```bash
backup_dir=/Users/luna/Documents/work/submit/backup
mkdir -p "$backup_dir"
backup_file="$backup_dir/wallpaper-$(date +%Y%m%d%H%M%S).sql.gz"
docker exec wallpaper-mysql-1 mysqldump -uluna -pluna --single-transaction --routines --triggers --no-tablespaces wallpaper | gzip > "$backup_file"
gzip -t "$backup_file"
shasum -a 256 "$backup_file" | tee "$backup_file.sha256"
```

## 分类和标签约定

- 分类表示图片单一主归属。
- 标签组表示描述维度。
- 标签必须属于一个标签组。
- 图片可以选择一个分类和多个启用标签。
- 标签停用后，图片库不再显示该标签，但历史关联可以保留。
- 标签组停用后，其下启用标签应一并停用；图片库也不显示停用标签组下的标签。

## 验证命令

后端：

```bash
cd backend
./mvnw test
./mvnw -DskipTests compile
```

前端：

```bash
cd frontend
npm run typecheck
npm run build
npm run test
```

Docker 配置：

```bash
cd ops/docker
docker compose -p wallpaper --env-file .env -f compose.yaml -f compose.build.yaml -f compose.local.yaml config
```

导入工具：

```bash
cd tools/image-importer
uv sync
uv run wallpaper-import
```

## Git 提交规范

提交信息使用 Conventional Commits：

```text
<type>(<scope>): <subject>
```

常用 `type`：

- `feat`: 新功能
- `fix`: 缺陷修复
- `refactor`: 重构
- `docs`: 文档更新
- `test`: 测试相关
- `build`: 构建、依赖、打包变更
- `ci`: CI/CD 配置变更
- `chore`: 杂项维护

约束：

- `subject` 简洁，不写句号。
- 优先使用小写英文。
- 一次提交只表达一个主要意图。
- 提交前执行 `git status --short`。
- 只暂存当前任务相关文件。
- 未经用户要求，不提交、不推送、不创建 PR。

示例：

```text
fix(taxonomy): hide disabled tags in image library
feat(rbac): add role permission editor
docs(workflow): add development handoff guide
test(images): cover disabled tag filtering
```

## 交付说明

最终回复应包含：

- 改了哪些关键文件。
- 执行了哪些验证命令。
- 哪些验证未执行及原因。
- 如果启动了后台服务，说明服务地址和状态。
