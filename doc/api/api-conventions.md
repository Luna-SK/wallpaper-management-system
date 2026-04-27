# API Conventions

## Base

统一前缀：`/api`

统一响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "optional-trace-id"
}
```

分页响应：

```json
{
  "items": [],
  "page": 1,
  "size": 20,
  "total": 0
}
```

## Main Endpoints

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/password-reset/request`
- `POST /api/auth/password-reset/confirm`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `PATCH /api/auth/profile`
- `PATCH /api/auth/password`
- `GET /api/users`
- `POST /api/users`
- `PATCH /api/users/{id}`
- `PUT /api/users/{id}/roles`
- `PUT /api/users/{id}/password`
- `POST /api/users/{id}/disable`
- `POST /api/users/{id}/enable`
- `DELETE /api/users/{id}/purge`
- `GET /api/roles`
- `POST /api/roles`
- `PATCH /api/roles/{id}`
- `PUT /api/roles/{id}/permissions`
- `POST /api/roles/{id}/disable`
- `POST /api/roles/{id}/enable`
- `DELETE /api/roles/{id}/purge`
- `GET /api/permissions`
- `POST /api/image-upload-sessions`
- `POST /api/image-upload-sessions/{id}/items`
- `POST /api/image-upload-sessions/{id}/items/{itemId}/retry`
- `POST /api/image-upload-sessions/{id}/confirm`
- `POST /api/image-upload-sessions/{id}/cancel`
- `GET /api/image-upload-sessions/{id}`
- `GET /api/image-upload-sessions/{id}/events`
- `POST /api/images/batch`（兼容旧调用）
- `GET /api/images`
- `GET /api/images/{id}`
- `PATCH /api/images/{id}`
- `POST /api/images/{id}/edit`
- `GET /api/images/{id}/edit-source`
- `GET /api/images/{id}/versions`
- `POST /api/images/{id}/versions/{versionId}/restore`
- `DELETE /api/images/{id}/versions/{versionId}`
- `DELETE /api/images/{id}`
- `GET /api/images/{id}/thumbnail`
- `GET /api/images/{id}/preview`
- `GET /api/images/{id}/download`
- `GET /api/categories`
- `POST /api/categories`
- `PATCH /api/categories/{id}`
- `POST /api/categories/{id}/restore`
- `DELETE /api/categories/{id}/purge?force=true`
- `GET /api/tag-groups`
- `POST /api/tag-groups`
- `PATCH /api/tag-groups/{id}`
- `POST /api/tag-groups/{id}/restore`
- `DELETE /api/tag-groups/{id}/purge?force=true`
- `GET /api/tags`
- `POST /api/tags`
- `PATCH /api/tags/{id}`
- `POST /api/tags/{id}/restore`
- `DELETE /api/tags/{id}/purge?force=true`
- `GET /api/audit-logs`
- `GET /api/audit-log-retention`
- `PATCH /api/audit-log-retention`
- `GET /api/audit-log-archives`
- `POST /api/audit-log-archives`
- `GET /api/statistics`
- `GET /api/system-settings`
- `PATCH /api/system-settings`

上传使用会话化接口：`POST /api/image-upload-sessions` 创建会话，`POST /api/image-upload-sessions/{id}/items` 上传文件到 RustFS 暂存区，`POST /api/image-upload-sessions/{id}/confirm` 确认入库，`POST /api/image-upload-sessions/{id}/cancel` 取消并清理未确认对象。创建会话必须包含 `categoryId`，并至少提交一个 `tagIds`；分类、标签和标签组都必须处于启用状态。旧 `POST /api/images/batch` 仅作为兼容接口保留。

确认入库前，暂存文件不会出现在图片库。取消会话、过期会话和孤儿对象清理都会删除未被 `image_versions` 引用的 RustFS 对象。

图片记录只关联一个分类。`GET /api/images` 和 `GET /api/images/{id}` 返回 `category` 对象或 `null`，`PATCH /api/images/{id}` 使用单个 `categoryId` 更新分类，标签使用 `tagIds` 多选且不受分类限制。标签响应包含 `groupId` 和 `groupName`。

在线图像编辑使用 `image:edit` 权限。前端通过 `GET /api/images/{id}/edit-source` 读取无水印当前版本作为编辑源，通过 `POST /api/images/{id}/edit` 提交编辑后的图片文件和操作摘要；后端创建新的 `image_versions` 记录并更新当前版本，原始对象不会被覆盖。

图片版本记录保存在 `image_versions`，版本对象保存在 RustFS，当前版本由 `images.current_version_id` 指向。`GET /api/images/{id}/versions` 使用 `image:view` 权限返回版本号、当前标识、操作类型、文件名、尺寸、大小、MIME 和创建时间；`POST /api/images/{id}/versions/{versionId}/restore` 使用 `image:edit` 权限把指定版本切为当前版本；`DELETE /api/images/{id}/versions/{versionId}` 使用 `image:delete` 权限删除非当前版本及其对象存储文件，当前版本禁止删除。

系统默认每张图片最多保留 5 个版本，包含当前版本。每次在线编辑保存新版本后，后端会按 `image.version.max_retained` 自动清理最早的非当前版本；定时清理链路也会补偿扫描历史超限数据，当前版本永远不会被自动清理。

图片水印由系统设置统一控制。启用后，`GET /api/images/{id}/preview`、`GET /api/images/{id}/download` 和 `POST /api/images/batch-download` 输出带文字水印的图片；缩略图和编辑源不叠加水印，数据库与对象存储中的原图保持无水印。

分类、标签组、标签的彻底删除只允许对已停用项执行。若存在图片、上传会话或下级标签引用，未传 `force=true` 时返回 `409 REFERENCE_EXISTS` 和引用数量；前端二次确认后可带 `force=true` 自动解除引用并物理删除。

## Auth and RBAC

认证使用短期 JWT access token 和服务端 refresh session。`POST /api/auth/login` 返回 `accessToken`、`refreshToken`、过期时间、当前用户资料、角色、权限编码和 `sessionPolicy`；后续请求使用 `Authorization: Bearer <accessToken>`。

`POST /api/auth/register` 公开注册启用用户，默认分配 `VIEWER` 角色并返回 token 对。`POST /api/auth/refresh` 使用 refresh token 轮换新的 token 对；refresh session 同时受空闲超时和绝对会话时长约束，默认空闲 2 小时退出、最长 7 天必须重新登录。`GET /api/auth/session-policy` 返回当前登录会话的空闲超时开关、空闲分钟数、绝对会话开关、绝对到期时间和服务器时间。`POST /api/auth/logout` 撤销当前 session。用户可通过 `PATCH /api/auth/profile` 修改个人资料，通过 `PATCH /api/auth/password` 修改自己的密码。管理员通过 `PUT /api/users/{id}/password` 重置用户密码，重置后该用户已有 session 会被撤销。

邮件找回密码使用公开接口。`POST /api/auth/password-reset/request` 接收邮箱并始终返回统一成功响应，避免暴露账号是否存在；若邮箱匹配启用用户，后端生成 30 分钟有效的一次性重置令牌，仅保存 SHA-256 哈希，并通过 SMTP 发送重置链接。`POST /api/auth/password-reset/confirm` 接收重置令牌和新密码，令牌有效且用户仍启用时更新密码、标记令牌已使用并撤销该用户全部 refresh session。

权限来自当前启用用户、启用角色和角色权限关系，请求鉴权时动态加载；角色权限调整后无需用户重新登录即可在下一次请求生效。开发令牌旁路默认关闭，仅在显式配置 `APP_SECURITY_DEVELOPMENT_TOKEN_ENABLED=true` 时启用。

用户生命周期接口使用 `user:manage` 权限：停用用户会设置 `DISABLED` 并撤销该用户 session，启用用户恢复为 `ACTIVE`，彻底删除仅允许已停用用户，且禁止删除当前登录用户和内置种子账号 `admin`、`manager`、`editor`、`viewer`。

角色生命周期接口使用 `role:manage` 权限：停用角色后不再参与后续请求的动态权限加载，启用角色恢复授权能力，彻底删除仅允许已停用角色，且禁止删除内置种子角色 `SYSTEM_ADMIN`、`DATA_MANAGER`、`TAG_EDITOR`、`VIEWER`。若角色仍被用户引用，返回 `409 REFERENCE_EXISTS`，响应 `data` 包含 `targetType`、`targetId` 和 `userCount`。

## System Settings

`GET /api/system-settings` 返回上传业务上限、后端上传硬上限、预览质量、已停用图片清理和水印版权保护配置：

```json
{
  "maxFileSizeMb": 10,
  "maxBatchSizeMb": 500,
  "maxFileHardLimitMb": 50,
  "maxBatchHardLimitMb": 500,
  "previewQuality": "ORIGINAL",
  "softDeleteRetentionDays": 180,
  "softDeleteCleanupEnabled": false,
  "softDeleteCleanupCron": "0 0 3 * * SUN",
  "watermarkEnabled": true,
  "watermarkPreviewEnabled": false,
  "watermarkText": "仅供授权使用",
  "watermarkMode": "CORNER",
  "watermarkPosition": "BOTTOM_RIGHT",
  "watermarkOpacityPercent": 16,
  "watermarkTileDensity": "SPARSE",
  "sessionIdleTimeoutEnabled": true,
  "sessionIdleTimeoutMinutes": 120,
  "sessionAbsoluteLifetimeEnabled": true,
  "sessionAbsoluteLifetimeDays": 7
}
```

`PATCH /api/system-settings` 需要 `setting:manage` 权限。上传业务上限不能超过硬上限；下载/导出水印与预览水印可独立开关，任一水印开启时必须填写不超过 64 个字符的水印文字；水印样式支持角落水印 `CORNER` 和斜向平铺 `TILED`，角落位置支持九宫格，透明度范围为 `5-40`，平铺密度支持 `SPARSE`、`NORMAL`、`DENSE`。空闲超时范围为 `15-1440` 分钟，绝对会话时长范围为 `1-30` 天，两个机制均可独立开关且默认开启。软删除自动清理 cron 使用 Spring 6 段表达式，默认每周日 03:00。保存后无需重启，下一次预览、下载或会话校验会读取最新配置，下一次调度会读取最新 cron；后端启动后仍会补偿检查一次已到期的停用图片。

## Audit Log Retention

审计日志近期数据保留在 MySQL，超过保留期的数据先归档到 RustFS，再从数据库批量清理。

`GET /api/audit-log-retention` 返回当前策略和待归档数量：

```json
{
  "settings": {
    "retentionDays": 180,
    "archiveEnabled": true,
    "archiveCron": "0 30 2 * * *",
    "archiveStorage": "RUSTFS",
    "batchSize": 1000
  },
  "expiredCount": 1280
}
```

`PATCH /api/audit-log-retention` 需要 `audit:manage` 权限，字段同 `settings`。
保存后无需重启服务，后端定时任务下一次调度会读取最新开关和 cron 表达式。
非法 cron、保留天数越界和批量大小越界返回 `VALIDATION_ERROR`。

`POST /api/audit-log-archives` 手动触发一次归档清理。归档写入失败时不得删除 `audit_logs`。

`GET /api/audit-log-archives` 返回归档运行历史，包括状态、保存条数、清理条数和 RustFS 对象路径。

## Errors

- `VALIDATION_ERROR`
- `UNAUTHORIZED`
- `FORBIDDEN`
- `RESOURCE_NOT_FOUND`
- `DUPLICATE_IMAGE`
- `UNSUPPORTED_FILE_TYPE`
- `STORAGE_ERROR`
- `INTERNAL_ERROR`
