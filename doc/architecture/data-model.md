# 数据模型

## User And RBAC

- `app_users`：账号、展示名、邮箱、手机、密码哈希、状态。
- `roles`：系统管理员、数据管理员、标签编辑人员、普通浏览用户。
- `permissions`：资源动作权限。
- `user_roles`、`role_permissions`：授权关系。

## Image

- `images`：图片元数据，包括标题、原文件名、SHA-256、MIME、大小、宽高、上传者、来源、状态、浏览量、下载量。
- `image_versions`：图片版本记录，保存原图、缩略图、高清预览、标准预览的 RustFS object key。覆盖编辑只切换当前版本指针，历史版本进入保留期。
- `image_objects`：保留为对象索引扩展表；首版运行主要以 `image_versions` 记录当前可用对象。
- `image_categories`：图片与分类的多对多关系。
- `image_tags`：图片与标签的多对多关系。
- `upload_batches`、`upload_batch_items`：批量上传进度、文件级状态、失败原因和重试记录。

## Taxonomy

- `categories`：图片分类，一期内置 `纺织瑕疵`。
- `tags`：分类下标签，用于搜索和补充描述。标签名不全局唯一，唯一约束为 `category_id + name`。

## Audit And Settings

- `audit_logs`：近期用户行为、对象类型、对象 ID、IP、浏览器信息和详情 JSON，默认保留最近 180 天。
- `audit_log_archives`：每次审计日志归档清理的运行记录，包括触发方式、截止时间、状态、归档对象路径、保存条数和清理条数。
- `system_settings`：上传限制、水印、预览质量、软删除保留期、备份策略、审计日志保留策略等配置。

审计日志历史数据不长期堆积在 MySQL。超过保留期的数据先写入 RustFS `jsonl.gz` 归档文件，写入成功后再批量删除对应数据库记录。
