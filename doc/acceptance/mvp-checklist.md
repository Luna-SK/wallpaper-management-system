# MVP 验收清单

## Backend

- Maven 测试通过。
- 后端本地配置只读取 `backend/.env`，并且仓库只提交 `backend/.env.example`。
- 后端默认端口为 `18090`，从 `backend/.env` 的 `SERVER_PORT` 读取。
- `backend/.env.example` 从原 `application.yml` 本地默认值迁移而来，且不包含 `DB_URL`。
- `backend/src/main/resources/application.yml` 使用 `DB_HOST`、`DB_PORT`、`DB_NAME` 拼接 JDBC URL。
- 项目内真实 `.env` 文件均被 `.gitignore` 忽略，只提交 `.env.example` 模板。
- `.env` 上传配置是硬上限，系统设置页上传配置是业务上限，业务上限不能超过硬上限。
- 上传服务按业务上限校验单文件大小和批量累计大小。
- 软删除自动清理默认关闭；开启后按软删除保留期和可配置 Spring cron 清理到期的已停用图片，默认每周日 03:00。
- 空库执行 Liquibase 成功。
- `spring.jpa.hibernate.ddl-auto=validate` 保持启用。
- `/api/system/health` 未登录可访问。
- 需要登录的接口未认证时返回拒绝。
- 审计日志表有保留期配置，超过保留期的日志先归档到 RustFS，再批量清理。
- RustFS 归档失败时不删除数据库审计日志。

## Frontend

- `npm run typecheck` 通过。
- `npm run build` 通过。
- 前端开发代理端口从 `frontend/.env` 的 `VITE_BACKEND_PORT` 读取，默认代理 `/api` 到 `http://localhost:18090`。
- 登录页、工作台布局、图片库、分类标签、用户权限、日志、统计、设置页面可访问。
- 图片库从 `/api/images` 读取数据，筛选、重置、单图上传、预览、编辑、下载、停用入口有实际行为。
- 图片库“上传图片”打开统一上传弹窗，弹窗内可切换单张/批量；侧边栏不再显示独立“批量上传”页面，`/upload` 重定向到 `/images`。
- 单张和批量上传都要求分类必填、标签至少选择 1 个，标签可搜索。
- 系统设置页上传上限输入范围受后端返回的硬上限约束。
- 上传先进入 RustFS 暂存区，确认入库后才出现在图片库；取消弹窗会清理本次未确认上传对象。
- JPG、PNG、WebP 上传后原图保留原质量，并生成列表缩略图、高清预览和标准预览。
- 图片重复上传时按 SHA-256 识别重复，不重复写入对象存储。
- RustFS 部分写入失败会立即补偿删除已写对象；过期未确认会话和孤儿对象由定时任务清理。
- 分类、标签、用户、角色均可通过页面新增或编辑；标签唯一性为“分类 + 标签名”。
- 审计日志页显示保留策略、归档历史和“立即归档并清理”入口。
- 系统设置页可以调整上传限制、预览质量、软删除保留期、已停用图片自动清理开关和执行计划、审计日志保留天数、自动归档开关、执行计划和批量大小。
- 修改审计日志保留策略后，审计日志页摘要随接口返回的最新配置变化。
- 分类标签页中 `纺织瑕疵` 显示为分类，其下展示 35 个标签。
- 用户权限页提供用户、角色、权限三个管理视图，角色支持新增、编辑和权限配置入口。
- 角色列表显示由权限集合动态生成的彩色分组摘要，原始权限编码在权限页展示。
- 统计页从 `/api/statistics` 展示图片总量、今日上传、浏览量、下载量和存储占用。
- 移动端宽度下无明显文字重叠。

## Deployment

- `docker compose -p wallpaper --env-file .env.example config` 通过。
- MySQL、Redis、RustFS、后端、前端服务配置完整。
- Compose project 名为 `wallpaper`，依赖端口为 MySQL `13316`、Redis `16389`、RustFS `19010/19011`。
- 备份 profile 可以生成压缩 SQL 文件。
- Docker Compose 只读取 `ops/docker/.env`，不读取或复用 `backend/.env`。
- Docker 后端容器默认监听并暴露 `18090`，前端 Nginx 代理 `/api` 到 `backend:18090`。
- `ops/docker/.env.example` 提供后端容器所需的数据库、Redis、RustFS、上传大小和安全令牌变量，且不包含 `DB_URL`。
- 真实 `backend/.env`、`ops/docker/.env`、其它项目内 `.env` 文件和前端本地 IDE 配置不应被暂存或提交。

## Branding

- 新项目中不出现原始框架品牌、作者推广、外部广告或旧接口前缀。
- UI 主体为图片管理系统。
