# 权限模型

## Roles

- 系统管理员：拥有全部管理权限，可管理用户、角色、系统设置、备份和所有图片。
- 数据管理员：管理图片、分类、标签、导入任务和数据质量。
- 标签编辑人员：上传图片、编辑元数据、维护授权范围内的分类和标签。
- 普通浏览用户：检索、预览、收藏和下载授权范围内的图片。

角色为数据库数据，不在前端写死。系统初始化提供上述四个默认角色，管理员后续可以新增、编辑、停用角色，并为角色配置权限。

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

后端必须在接口层和方法层同时表达敏感操作权限。前端菜单只作为体验优化，不作为安全边界。

`audit:view` 允许查看近期审计日志和归档历史；`audit:manage` 允许修改保留策略并手动触发归档清理。
