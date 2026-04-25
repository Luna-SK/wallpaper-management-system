# 系统架构

## 模块

- `auth`：登录、注册、刷新令牌、密码找回和重置。
- `users`：用户档案、账号状态、个人信息。
- `rbac`：角色、权限、资源动作、菜单授权。
- `images`：上传、对象存储、预览、元数据、访问统计。
- `taxonomy`：分类和分类下标签。
- `audit`：操作日志、访问日志、下载日志、归档和保留期清理。
- `settings`：上传限制、预览质量、软删除自动清理、审计日志保留策略。
- `backup`：数据库备份、对象存储备份策略。
- `statistics`：图片数量、访问量、下载量、存储占用。

## Runtime

前端由 Nginx 提供静态资源并代理 `/api` 到后端。后端连接 MySQL、Redis 和 RustFS。数据库结构只由 Liquibase 管理，应用运行期不自动建表或改表。

## Configuration Boundaries

后端本地开发只读取 `backend/.env`。`backend/src/main/resources/application.yml` 通过 `spring.config.import=optional:file:.env[.properties]` 导入当前工作目录下的 `.env`，因此本地启动命令需要在 `backend/` 目录执行。后端不在 `.env` 中维护 `DB_URL`，而是通过 `DB_HOST`、`DB_PORT`、`DB_NAME` 拼接 JDBC URL；`DB_NAME` 表示数据库名，`DB_USERNAME` 表示数据库登录用户。

Docker Compose 只读取 `ops/docker/.env`，并把其中的值作为容器环境变量注入后端容器；后端容器不会读取 `backend/.env`。Docker 默认让后端容器监听并暴露 `18090`，前端 Nginx 代理 `/api` 到 `backend:18090`。

前端开发环境只读取 `frontend/.env`。Vite dev server 保持业务请求为 `/api`，并通过 `VITE_BACKEND_PORT` 代理到本地后端，默认端口为 `18090`。前端不读取后端或 Docker Compose 的 `.env` 文件。

项目内真实 `.env` 文件均不进入版本库；只提交 `.env.example` 模板。新增子项目如果需要本地环境文件，也必须加入同一忽略策略，避免密钥和本地端口配置泄露。

上传限制分为两层：`.env` 中的 `UPLOAD_MAX_FILE_SIZE` 和 `UPLOAD_MAX_REQUEST_SIZE` 是服务启动时的硬上限；系统设置页中的单文件上限和批量上传上限是数据库业务配置，运行时可调，但不能超过硬上限。上传服务按两者取较小值校验。

## Storage Flow

1. 前端提交图片文件和分类标签信息。
2. 后端校验文件头、MIME、大小和权限。
3. 后端计算 SHA-256，用于去重和审计。
4. 原图写入 RustFS 原图桶。
5. 后端生成高清预览图、标准预览图和缩略图。
6. 元数据、对象 key、标签关系和审计日志写入 MySQL。

## Soft Delete Retention Flow

1. 图片停用后状态变为 `DELETED`，并记录 `deleted_at`。
2. 管理员可在系统设置页配置软删除保留期、自动清理开关和清理执行计划。
3. 自动清理默认关闭；开启后，后端按 Spring cron 调度清理，默认每周日 03:00。
4. 应用启动完成后会立即执行一次补偿检查，覆盖服务停机期间已经到期的已停用图片。
5. 到期图片复用彻底删除逻辑，删除数据库记录和 RustFS 中对应对象，并写入审计日志。

## Audit Retention Flow

1. 近期审计日志保存在 MySQL `audit_logs`，用于页面查询。
2. 定时任务按系统设置计算保留期截止时间，默认每天 02:30 处理 180 天以前的记录。
3. 后端将待归档日志写为 gzip JSONL，并保存到 RustFS 审计归档桶。
4. RustFS 写入成功后，后端按批次删除已经归档的数据库记录。
5. 每次运行都写入 `audit_log_archives`，便于查看成功、失败和归档对象路径。
6. 管理员在系统设置页修改保留天数、自动归档开关或执行时间后无需重启服务，下一次调度按最新配置执行。
