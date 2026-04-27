# 权限模型

## Roles

- 系统管理员：拥有全部管理权限，可管理用户、角色、系统设置、备份和所有图片。
- 数据管理员：管理图片、分类、标签、导入任务和数据质量。
- 标签编辑人员：上传图片、编辑元数据、维护授权范围内的分类和标签。
- 普通浏览用户：检索、预览、收藏和下载授权范围内的图片。

角色为数据库数据，不在前端写死。系统初始化提供上述四个默认角色，管理员后续可以新增、编辑、停用、启用和彻底删除非内置角色，并为角色配置权限。

用户状态使用 `ACTIVE` / `DISABLED`。停用用户会撤销该用户已有 session；启用用户允许重新登录；彻底删除只允许已停用用户，并禁止删除当前登录用户和内置种子账号。

角色状态使用 `enabled`。停用角色后，该角色不会参与后续请求的动态权限加载；彻底删除只允许已停用且未被用户引用的非内置角色，被引用时返回引用用户数量。

角色列表中的权限摘要必须由 `role_permissions` 和 `permissions` 动态生成。`roles.description` 只能作为可选备注，不能作为权限范围或页面摘要的事实来源。
角色列表面向业务扫描，使用按资源聚合的摘要；权限页保留 `resource:action` 编码用于维护和核对。

## Permission Shape

权限编码采用 `resource:action`：

- `image:view`
- `image:upload`
- `image:edit`
- `image:delete`
- `taxonomy:manage`
- `user:manage`
- `role:manage`
- `audit:view`
- `audit:manage`
- `setting:manage`
- `backup:manage`

后端必须在接口层和方法层同时表达敏感操作权限。前端菜单、路由和操作按钮按权限收敛展示，只作为体验优化，不作为安全边界。

`audit:view` 允许查看近期审计日志和归档历史；`audit:manage` 允许修改保留策略并手动触发归档清理。

## Authentication Runtime

认证使用短期 JWT access token 和服务端 refresh session。access token 携带 `sub=userId`、`sid=sessionId` 和过期时间；请求进入后端时会校验 token、session 是否撤销或过期、空闲超时和绝对会话时长是否触发、用户是否启用，并按用户当前启用角色实时加载权限。

refresh token 只以哈希形式保存在 `auth_refresh_tokens`。刷新接口会轮换 refresh token 并更新 `last_activity_at`；空闲超时默认 2 小时，绝对会话时长默认 7 天，两个机制都可在系统设置中独立开关和配置。退出登录撤销当前 session；用户自助改密会撤销其它 session；管理员重置密码和停用用户会撤销该用户全部 session。开发令牌旁路默认关闭，仅测试或本地显式开启时可用。
