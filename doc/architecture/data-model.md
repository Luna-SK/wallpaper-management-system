# 数据模型

## 用户与权限

- `app_users`：用户名、展示名、邮箱、手机、密码哈希、状态、头像对象 key 和头像 MIME；状态为 `ACTIVE` 或 `DISABLED`。邮箱允许为空，非空邮箱统一保存为小写并保持唯一，用于邮件找回密码。用户头像原图不入库，后端统一裁剪缩放成 160x160 PNG 后保存到 RustFS `avatars/{userId}/` 前缀。
- `auth_refresh_tokens`：refresh/session 记录，保存 token 哈希、用户、过期时间、撤销时间、最近活动时间、创建 IP 和 User-Agent。
- `password_reset_tokens`：邮件找回密码令牌，保存用户、令牌哈希、过期时间、使用时间、请求 IP 和 User-Agent；明文令牌只出现在邮件链接中，不入库。
- `roles`：系统管理员、数据管理员、标签编辑人员、普通浏览用户；`enabled=false` 表示角色已停用。
- `permissions`：资源动作权限。
- `user_roles`、`role_permissions`：授权关系。

用户彻底删除只允许对已停用且非内置、非当前登录用户执行，删除前会清理 refresh session 和用户角色关系。角色彻底删除只允许对已停用且非内置角色执行；如果仍被用户引用，后端拒绝删除并返回引用数量。

## 图片

- `images`：图片元数据，包括标题、原文件名、SHA-256、MIME、大小、宽高、上传者、来源、单一分类、状态、浏览量、下载量。
- `image_versions`：图片版本记录，保存原图、缩略图、高清预览、标准预览的 RustFS object key。覆盖编辑只切换当前版本指针，历史版本进入保留期；版本号按单张图片现有最大版本号递增。
- `image_tags`：图片与标签的多对多关系。
- `upload_batches`、`upload_batch_items`、`upload_batch_tags`：上传会话、文件级状态、会话标签、暂存对象 key、失败原因和重试记录。文件先暂存到 RustFS，确认后才创建正式 `images + image_versions` 记录；取消或过期会话会清理未确认对象。
- `image_comments`：图片评论，绑定图片和用户，状态为 `ACTIVE` 或 `DELETED`；通过 `parent_comment_id`、`root_comment_id` 和 `depth` 支持无限层级楼中楼回复。删除评论采用软状态，若下方仍有回复则保留占位节点以维持讨论上下文；评论响应会从用户头像字段派生作者头像 URL。
- `image_favorites`：图片收藏，使用 `user_id + image_id` 唯一约束，取消收藏时物理删除关系。
- `image_likes`：图片点赞，使用 `user_id + image_id` 唯一约束，取消点赞时物理删除关系。
- `user_feedback`：用户反馈工单，绑定提交用户，可选关联图片，状态为 `OPEN`、`IN_PROGRESS`、`RESOLVED`、`CLOSED`，管理员处理时记录回复、处理人和处理时间。

`image_objects` 不再作为活动模型或 Liquibase 基线表存在。当前项目按全新部署维护干净基线，空库初始化会直接创建最终表结构；已有旧库不能直接套用该基线，需先备份并使用空库或重置数据卷重新初始化。

旧版本图片存放在 `image_versions + RustFS` 中，当前版本由 `images.current_version_id` 指向。恢复版本只切换当前版本指针并同步 `images` 当前元信息，不复制对象；删除版本只允许删除非当前版本，并同步删除该版本的原图、缩略图和预览对象。系统设置 `image.version.max_retained` 控制单张图片版本保留上限，默认 5 个且包含当前版本；新增编辑版本和定时清理都会自动删除最早的非当前超限版本，当前版本不会被自动清理。

图片彻底删除前会清理评论、收藏、点赞关系，并将关联反馈的 `image_id` 置空，避免反馈工单因图片生命周期结束而丢失。用户彻底删除前会清理该用户的评论、收藏、点赞和反馈数据。

## 分类与标签

- `categories`：图片主分类，一张图片最多关联一个分类；一期保留 `纺织瑕疵`。
- `tag_groups`：标签组，表示标签维度，例如 `瑕疵`、风格、颜色、材质。
- `tags`：标签属于一个标签组，用于检索和补充描述。标签唯一约束为 `group_id + name`。
- `image_tags`：图片与标签的多对多关系；标签不再受图片分类限制。

分类、标签组和标签都使用 `enabled=false` 表示已停用。已停用项支持彻底删除；若仍被图片或上传会话引用，后端在 `force=true` 时会先解除引用再物理删除。

## 审计与设置

- `audit_logs`：近期用户行为、对象类型、对象 ID、IP、浏览器信息和详情 JSON，默认保留最近 180 天。
- `audit_log_archives`：每次审计日志归档清理的运行记录，包括触发方式、截止时间、状态、归档对象路径、保存条数和清理条数。
- `system_settings`：上传限制、预览质量、软删除保留期、软删除自动清理开关与 cron、备份策略、审计日志保留策略等配置。

审计日志历史数据不长期堆积在 MySQL。超过保留期的数据先写入 RustFS `jsonl.gz` 归档文件，写入成功后再批量删除对应数据库记录。

已停用图片不立即物理删除。启用软删除自动清理后，系统按 `soft_delete.retention_days` 和 `soft_delete.cleanup.cron` 清理超过保留期的 `DELETED` 图片；默认清理计划为每周日 03:00，且服务启动后会补偿检查一次。
