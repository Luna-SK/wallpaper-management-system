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
- `POST /api/auth/logout`
- `POST /api/auth/password/forgot`
- `POST /api/auth/password/reset`
- `GET /api/users`
- `POST /api/users`
- `PATCH /api/users/{id}`
- `PUT /api/users/{id}/roles`
- `GET /api/roles`
- `POST /api/roles`
- `PATCH /api/roles/{id}`
- `PUT /api/roles/{id}/permissions`
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

分类、标签组、标签的彻底删除只允许对已停用项执行。若存在图片、上传会话或下级标签引用，未传 `force=true` 时返回 `409 REFERENCE_EXISTS` 和引用数量；前端二次确认后可带 `force=true` 自动解除引用并物理删除。

## System Settings

`GET /api/system-settings` 返回上传业务上限、后端上传硬上限、预览质量和已停用图片清理配置：

```json
{
  "maxFileSizeMb": 10,
  "maxBatchSizeMb": 500,
  "maxFileHardLimitMb": 50,
  "maxBatchHardLimitMb": 500,
  "previewQuality": "ORIGINAL",
  "softDeleteRetentionDays": 180,
  "softDeleteCleanupEnabled": false,
  "softDeleteCleanupCron": "0 0 3 * * SUN"
}
```

`PATCH /api/system-settings` 需要 `setting:manage` 权限。上传业务上限不能超过硬上限；软删除自动清理 cron 使用 Spring 6 段表达式，默认每周日 03:00。保存后无需重启，下一次调度会读取最新 cron；后端启动后仍会补偿检查一次已到期的停用图片。

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
- `AUTHENTICATION_REQUIRED`
- `ACCESS_DENIED`
- `RESOURCE_NOT_FOUND`
- `DUPLICATE_IMAGE`
- `UNSUPPORTED_FILE_TYPE`
- `STORAGE_ERROR`
- `INTERNAL_ERROR`
