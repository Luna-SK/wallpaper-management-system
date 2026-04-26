# 项目规划

## 目标

`wzut-wallpaper-manager` 是图片管理系统的新实现。系统面向图片资产管理，支持上传、分类、标签组、标签、检索、预览、权限控制、审计日志、统计和备份。

一期把纺织瑕疵图片作为内置业务场景，但不把系统限制为瑕疵管理平台。图片通过单一主分类确定业务归属，通过标签组和标签表达风格、颜色、材质、瑕疵等检索维度。

## 技术栈

- 后端：Spring Boot 4.0.x、Java 25 LTS、Maven、Spring Security、MyBatis-Plus、Liquibase。
- 前端：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus。
- 存储：RustFS，通过 S3 兼容接口访问。
- 数据库：MySQL 8.4 LTS。
- 缓存：Redis。
- 部署：单机 Docker Compose。

## 工程协作入口

新建开发上下文时，以 `wzut-wallpaper-manager/` 为项目根目录，优先阅读：

- `doc/development/workflow.md`：本地启动、验证、数据库迁移和 Git 提交流程。

提交信息采用 Conventional Commits：`<type>(<scope>): <subject>`，例如 `fix(taxonomy): hide disabled tags in image library`。

## 一期交付

- 用户注册、登录、用户自助改密、管理员重置密码和个人信息修改。
- 四角色权限模型：系统管理员、数据管理员、标签编辑人员、普通浏览用户。
- 图片上传、批量上传、原图保留、预览图、缩略图。
- 分类、标签组和标签，一期内置 `纺织瑕疵` 分类、`瑕疵` 标签组及 35 个瑕疵标签。
- 关键词、分类、标签、上传者、时间范围检索。
- 鉴权访问、原图下载、操作日志、访问日志。
- 图片数量、上传量、浏览量、下载量、存储占用统计。
- Docker 部署、空库初始化、备份脚本。

## 非一期范围

- 完整在线裁剪、旋转、批注和复杂图片编辑器。
- 评论、点赞、反馈工单。
- 相似图检索、AI 自动识别、模型训练工作流。
- 多机高可用部署。
