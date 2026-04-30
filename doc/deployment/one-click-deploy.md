# 跨平台 Docker 一键部署

一键部署的目标是“一条命令完成环境生成、构建或拉取、启动和健康检查”。脚本不会提交真实 `.env`，也不会在已有 `ops/docker/.env` 存在时静默覆盖它；需要重新生成时显式加 `--force-env` 或 `-ForceEnv`。

如果已有 `ops/docker/.env` 仍包含 `change-me` 占位值，脚本会友好退出并提示处理；演示机可以使用 `--force-env` / `-ForceEnv` 重新生成强随机配置。

## 前置条件

- 已安装 Docker Desktop，或 Linux 服务器已安装 Docker Engine 与 Docker Compose v2。
- 生产 HTTPS 需要域名已解析到服务器公网 IP，并开放 `80`、`443` 入站端口。
- 生产服务器建议先关闭安全组中的 MySQL、Redis、RustFS 和后端调试端口；本项目生产 Compose 默认也不会发布这些端口。

## 最低依赖与推荐规格

最少依赖：

- Windows：Docker Desktop、Docker Compose v2、PowerShell 5.1+。
- macOS / Linux：Docker Desktop 或 Docker Engine、Docker Compose v2、`sh` 和基础 Unix 工具。
- 宿主机不需要安装 Java、Node、Maven 或 npm；默认构建发生在 Docker 镜像内部，使用预构建镜像时只需要拉取镜像。

资源规格建议：

| 场景 | 最低配置 | 推荐配置 | 说明 |
|------|----------|----------|------|
| 本地演示 | 2 CPU、4 GiB 内存、20 GiB 可用磁盘 | 4 CPU、8 GiB 内存、40 GiB+ 可用磁盘 | 适合完整启动 MySQL、Redis、RustFS、backend 和 frontend |
| Linux 云服务器小规模演示 | 1-2 vCPU、2 GiB 内存、30 GiB 可用磁盘 | 2 vCPU、4 GiB 内存、40 GiB+ 可用磁盘 | 最低配置建议只使用预构建镜像，不建议在服务器本地构建 |
| 大批量图片导入或多人演示 | 2 vCPU、4 GiB 内存、40 GiB+ 可用磁盘 | 4 vCPU、8 GiB 内存、80 GiB+ 可用磁盘 | 图片上传会占用后端、对象存储和磁盘 I/O，规格越低越应降低批次大小 |

小内存 ECS 不建议直接执行本地镜像构建；优先使用 `--use-prebuilt-images` 拉取预构建镜像，或在本机按目标架构预构建镜像后上传到服务器。

## 失败提示

脚本遇到依赖不满足或 Docker 执行失败时，会输出中文友好提示后退出，不会把 Docker/Compose 的长错误直接刷到终端。底层详情会写入 `ops/docker/deploy-error.log`，常见提示包括：

- Docker 未安装：提示安装 Docker Desktop 或 Docker Engine。
- Docker 已安装但未运行：提示启动 Docker Desktop，Linux 检查 Docker 服务和当前用户权限。
- Compose v2 不可用：提示需要 `docker compose version` 可用，不支持旧版 `docker-compose`。
- 端口占用、镜像拉取失败、网络不可达或磁盘不足：提示查看 `deploy-error.log`。
- 健康检查超时：提示查看容器状态和 backend/frontend 日志。

## 本地演示

macOS / Linux：

```bash
sh scripts/deploy.sh --mode local
```

Windows PowerShell：

```powershell
.\scripts\deploy.ps1 -Mode local
```

Windows CMD：

```cmd
deploy.cmd -Mode local
```

脚本完成后会打印前端地址、`admin` 用户名和初始密码。默认访问地址为 `http://localhost`。本地模式会发布调试端口：MySQL `13316`、Redis `16389`、RustFS API `19010`、RustFS 控制台 `19011`、后端 `18090`、前端 `80`。

启用本地邮件捕获：

```bash
sh scripts/deploy.sh --mode local --mailpit
```

PowerShell：

```powershell
.\scripts\deploy.ps1 -Mode local -Mailpit
```

Mailpit 启动后访问 `http://localhost:8025`。邮件找回密码业务开关仍默认关闭；需要先把 `APP_MAIL_ENABLED=true` 写入 `ops/docker/.env` 或临时传入环境变量，再由管理员在系统设置中开启。未加 `--mailpit` / `-Mailpit` 时，部署脚本不会生成 Mailpit SMTP 主机配置，也不会启动 Mailpit 容器。

## Linux 云服务器

HTTP：

```bash
sh scripts/deploy.sh --mode production --domain example.com
```

HTTPS：

```bash
sh scripts/deploy.sh --mode production --domain example.com --email admin@example.com --https
```

生产模式默认：

- 只公开 Web 入口；MySQL、Redis、RustFS、backend 调试端口不发布到公网。
- 自动生成数据库密码、Redis 密码、RustFS 密钥、JWT 密钥、开发令牌和初始 `admin` 密码。
- `APP_BOOTSTRAP_DEMO_USERS_ENABLED=false`，首次启动会禁用 `manager`、`editor`、`viewer` 演示账号。
- 邮件找回密码默认关闭。

首次登录后建议立刻在系统内修改 `admin` 密码，并把脚本输出的初始密码从终端历史或运维记录中妥善处理。

## 使用预构建镜像

默认脚本会在目标机器本地构建 backend/frontend 镜像。若已通过 GitHub Actions 发布镜像到 GHCR，可改为拉取镜像：

```bash
sh scripts/deploy.sh --mode production --domain example.com --https --email admin@example.com --use-prebuilt-images
```

PowerShell：

```powershell
.\scripts\deploy.ps1 -Mode production -Domain example.com -Https -Email admin@example.com -UsePrebuiltImages
```

默认镜像名：

- `ghcr.io/luna-sk/wallpaper-management-system/backend:latest`
- `ghcr.io/luna-sk/wallpaper-management-system/frontend:latest`

如需使用私有镜像仓库，修改 `ops/docker/.env` 中的 `BACKEND_IMAGE`、`FRONTEND_IMAGE` 和 `IMAGE_TAG`。

## 常用参数

Shell：

```bash
sh scripts/deploy.sh --help
```

PowerShell：

```powershell
Get-Help .\scripts\deploy.ps1
```

常用项：

- `--mode local|production` / `-Mode local|production`：部署模式。
- `--frontend-port 8080` / `-FrontendPort 8080`：本地模式前端宿主机端口。
- `--backend-port 18090` / `-BackendPort 18090`：本地模式后端调试端口。
- `--domain example.com` / `-Domain example.com`：生产访问域名。
- `--https` / `-Https`：启用 Caddy 自动 HTTPS。
- `--email admin@example.com` / `-Email admin@example.com`：申请证书使用的邮箱。
- `--mailpit` / `-Mailpit`：启动本地邮件捕获服务。
- `--use-prebuilt-images` / `-UsePrebuiltImages`：拉取预构建镜像。
- `--dry-run` / `-DryRun`：只生成和校验配置，不启动容器。
- `--force-env` / `-ForceEnv`：重新生成 `ops/docker/.env`。

## 邮件找回密码

一键部署不会自动开启邮件找回密码。生产启用流程：

1. 修改 `ops/docker/.env` 中的 `APP_MAIL_*` SMTP 配置。
2. 根据服务商选择 STARTTLS 或 SSL：
   - STARTTLS：`APP_MAIL_SMTP_STARTTLS=true`，`APP_MAIL_SMTP_SSL=false`。
   - 465 SSL：`APP_MAIL_SMTP_SSL=true`，`APP_MAIL_SMTP_STARTTLS=false`。
3. 重启 backend：`docker compose -p wallpaper --env-file ops/docker/.env -f ops/docker/compose.yaml up -d backend`。
4. 管理员登录系统设置，开启“邮件找回密码”。

若 SMTP 配置错误或邮件服务不可用，申请接口只返回服务不可用提示，应用不会崩溃。

## 验证与维护

配置校验：

```bash
cd ops/docker
docker compose -f compose.yaml -f compose.build.yaml -f compose.local.yaml --env-file .env.example config
docker compose -f compose.yaml -f compose.release.yaml --env-file .env.example config
```

HTTPS 配置校验需要 `.env` 中存在 `CADDY_DOMAIN` 和 `CADDY_EMAIL`：

```bash
docker compose -f compose.yaml -f compose.https.yaml --env-file .env config
```

升级镜像：

```bash
cd ops/docker
docker compose -p wallpaper --env-file .env -f compose.yaml -f compose.build.yaml -f compose.local.yaml pull
docker compose -p wallpaper --env-file .env -f compose.yaml -f compose.build.yaml -f compose.local.yaml up -d --build
```

涉及 Docker MySQL 的 schema 或数据迁移前，先执行数据库备份，并完成 `gzip -t` 与 `shasum -a 256` 校验。对象存储备份和恢复流程见 [docker-compose.md](docker-compose.md)。
