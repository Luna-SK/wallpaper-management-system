# Docker Compose 部署

## Services

- `mysql`：MySQL 8.4 LTS。
- `redis`：Redis 7，缓存和短期令牌辅助存储。
- `rustfs`：RustFS 1.0.0-alpha.98，S3 兼容对象存储。
- `backend`：Spring Boot API。
- `frontend`：Nginx 静态资源和 API 反向代理。
- `backup`：按 profile 手动执行数据库备份。

## Project Name And Ports

新项目不复用旧项目 Docker Compose。统一使用 `wallpaper` 作为 Compose project 名：

```bash
docker compose -p wallpaper --env-file .env up -d
```

默认端口映射：

- MySQL：`13316:3306`
- Redis：`16389:6379`
- RustFS API：`19010:9000`
- RustFS Console：`19011:9001`

## Commands

```bash
cd ops/docker
cp .env.example .env
docker compose -p wallpaper --env-file .env config
docker compose -p wallpaper --env-file .env up -d --build
docker compose -p wallpaper logs -f backend
```

备份：

```bash
docker compose -p wallpaper --env-file .env --profile backup run --rm backup
```

## Notes

- `.env.example` 只提供占位值，真实部署必须替换密钥。
- 依赖镜像使用本地已存在版本：`mysql:8.4`、`redis:7`、`rustfs/rustfs:1.0.0-alpha.98`。
- Nginx 和后端上传大小上限均按 500MB 批量请求设计。
- 数据库结构由后端启动时的 Liquibase 初始化完成。
- 审计日志历史归档写入 RustFS，默认 bucket 为 `wallpaper-audit`，对象路径形如 `audit-logs/2026/04/audit-log-archive-20260424-023000.jsonl.gz`。
- 审计日志归档文件是 gzip JSONL，可下载后解压按行解析；只有写入成功的日志才会从 MySQL 清理。
