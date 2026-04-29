#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
COMPOSE_DIR="$ROOT_DIR/ops/docker"
ENV_FILE="$COMPOSE_DIR/.env"
ERROR_LOG="$COMPOSE_DIR/deploy-error.log"

mode=local
project=wallpaper
frontend_port=80
backend_port=18090
domain=
email=
https=false
mailpit=false
use_prebuilt=false
force_env=false
dry_run=false
skip_build=false

usage() {
	cat <<'EOF'
Usage: sh scripts/deploy.sh [options]

Options:
  --mode local|production      Deployment mode. Default: local
  --frontend-port PORT         Host HTTP port in local mode. Default: 80
  --backend-port PORT          Host backend debug port in local mode. Default: 18090
  --domain DOMAIN              Public domain for production access
  --email EMAIL                ACME email for HTTPS certificates
  --https                      Enable Caddy HTTPS reverse proxy
  --mailpit                    Enable local Mailpit SMTP/web UI profile
  --use-prebuilt-images        Pull backend/frontend images instead of local build
  --project NAME               Docker Compose project name. Default: wallpaper
  --force-env                  Regenerate ops/docker/.env even if it exists
  --dry-run                    Generate/validate config without starting containers
  --skip-build                 Start without --build when using local Dockerfiles
  -h, --help                   Show this help
EOF
}

warn() {
	printf '提示：%s\n' "$*" >&2
}

clear_error_log() {
	rm -f "$ERROR_LOG"
}

fail() {
	reason="$1"
	advice="$2"
	exit_code="${3:-1}"
	printf '\n部署未完成：%s\n' "$reason" >&2
	printf '建议：%s\n' "$advice" >&2
	if [ -s "$ERROR_LOG" ]; then
		printf '详情日志：%s\n' "$ERROR_LOG" >&2
	fi
	exit "$exit_code"
}

run_quiet() {
	reason="$1"
	advice="$2"
	shift 2
	: > "$ERROR_LOG"
	if "$@" >>"$ERROR_LOG" 2>&1; then
		clear_error_log
		return 0
	fi
	fail "$reason" "$advice"
}

run_quiet_eval() {
	reason="$1"
	advice="$2"
	command_text="$3"
	: > "$ERROR_LOG"
	if sh -c "$command_text" >>"$ERROR_LOG" 2>&1; then
		clear_error_log
		return 0
	fi
	fail "$reason" "$advice"
}

clear_error_log

while [ "$#" -gt 0 ]; do
	case "$1" in
		--mode)
			mode="${2:-}"
			shift 2
			;;
		--frontend-port)
			frontend_port="${2:-}"
			shift 2
			;;
		--backend-port)
			backend_port="${2:-}"
			shift 2
			;;
		--domain)
			domain="${2:-}"
			shift 2
			;;
		--email)
			email="${2:-}"
			shift 2
			;;
		--https)
			https=true
			shift
			;;
		--mailpit)
			mailpit=true
			shift
			;;
		--use-prebuilt-images|--pull-images)
			use_prebuilt=true
			shift
			;;
		--project)
			project="${2:-}"
			shift 2
			;;
		--force-env)
			force_env=true
			shift
			;;
		--dry-run)
			dry_run=true
			shift
			;;
		--skip-build)
			skip_build=true
			shift
			;;
		-h|--help)
			usage
			exit 0
			;;
		*)
			fail "参数错误：未知选项 $1" "请运行 sh scripts/deploy.sh --help 查看可用参数。" 2
			;;
	esac
done

if [ "$mode" != "local" ] && [ "$mode" != "production" ]; then
	fail "参数错误：--mode 只能是 local 或 production" "本地演示使用 --mode local，生产部署使用 --mode production。" 2
fi

if [ "$https" = true ] && { [ -z "$domain" ] || [ -z "$email" ]; }; then
	fail "参数错误：启用 HTTPS 时缺少域名或邮箱" "请使用：sh scripts/deploy.sh --mode production --domain example.com --email admin@example.com --https" 2
fi
if [ "$https" = true ] && [ "$mode" != "production" ]; then
	fail "参数错误：HTTPS 只支持生产模式" "请使用 --mode production，或在本地模式去掉 --https。" 2
fi
if [ "$mailpit" = true ] && [ "$mode" != "local" ]; then
	fail "参数错误：Mailpit 只用于本地邮件测试" "请使用 --mode local --mailpit；生产模式请配置真实 SMTP 服务。" 2
fi

require_command() {
	if ! command -v "$1" >/dev/null 2>&1; then
		fail "未检测到 $1" "请先安装 Docker Desktop，或在 Linux 服务器安装 Docker Engine。"
	fi
}

random_hex() {
	bytes="$1"
	if command -v openssl >/dev/null 2>&1; then
		openssl rand -hex "$bytes"
	else
		LC_ALL=C tr -dc 'a-f0-9' </dev/urandom | dd bs=1 count=$((bytes * 2)) 2>/dev/null
	fi
}

random_alnum() {
	length="$1"
	if command -v openssl >/dev/null 2>&1; then
		openssl rand -base64 $((length * 2)) | LC_ALL=C tr -dc 'A-Za-z0-9' | dd bs=1 count="$length" 2>/dev/null
	else
		LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | dd bs=1 count="$length" 2>/dev/null
	fi
}

url_with_port() {
	scheme="$1"
	host="$2"
	port="$3"
	if [ "$port" = "80" ] && [ "$scheme" = "http" ]; then
		printf '%s://%s' "$scheme" "$host"
	elif [ "$port" = "443" ] && [ "$scheme" = "https" ]; then
		printf '%s://%s' "$scheme" "$host"
	else
		printf '%s://%s:%s' "$scheme" "$host" "$port"
	fi
}

get_env_value() {
	key="$1"
	awk -F= -v key="$key" '$1 == key { print substr($0, index($0, "=") + 1); exit }' "$ENV_FILE"
}

write_env_file() {
	db_password=$(random_alnum 28)
	db_root_password=$(random_alnum 32)
	redis_password=$(random_alnum 32)
	rustfs_access_key=$(random_alnum 24)
	rustfs_secret_key=$(random_alnum 40)
	jwt_secret=$(random_alnum 64)
	dev_token=$(random_alnum 40)
	admin_password=$(random_alnum 20)
	image_tag=local
	backend_image=wallpaper/backend:local
	frontend_image=wallpaper/frontend:local
	demo_users_enabled=true
	mail_host=
	mail_port=25

	if [ "$mode" = "production" ]; then
		demo_users_enabled=false
	fi
	if [ "$mailpit" = true ]; then
		mail_host=mailpit
		mail_port=1025
	fi

	if [ "$use_prebuilt" = true ]; then
		image_tag=latest
		backend_image=ghcr.io/luna-sk/wallpaper-management-system/backend:latest
		frontend_image=ghcr.io/luna-sk/wallpaper-management-system/frontend:latest
	fi

	if [ "$https" = true ]; then
		frontend_base_url="https://$domain"
	elif [ -n "$domain" ]; then
		frontend_base_url=$(url_with_port http "$domain" "$frontend_port")
	else
		frontend_base_url=$(url_with_port http localhost "$frontend_port")
	fi

	allowed_origins="$frontend_base_url,http://localhost:$frontend_port,http://127.0.0.1:$frontend_port"

	cat >"$ENV_FILE" <<EOF
DB_NAME=wallpaper
DB_USERNAME=wallpaper
DB_PASSWORD=$db_password
DB_ROOT_PASSWORD=$db_root_password

IMAGE_TAG=$image_tag
BACKEND_IMAGE=$backend_image
FRONTEND_IMAGE=$frontend_image

MYSQL_PORT=13316
REDIS_PORT=16389
BACKEND_PORT=$backend_port
FRONTEND_PORT=$frontend_port
MAILPIT_SMTP_PORT=1025
MAILPIT_WEB_PORT=8025
HTTP_PORT=80
HTTPS_PORT=443
CADDY_DOMAIN=$domain
CADDY_EMAIL=$email

REDIS_PASSWORD=$redis_password

RUSTFS_API_PORT=19010
RUSTFS_CONSOLE_PORT=19011
RUSTFS_REGION=cn-east-1
RUSTFS_ACCESS_KEY=$rustfs_access_key
RUSTFS_SECRET_KEY=$rustfs_secret_key
RUSTFS_BUCKET_ORIGINAL=wallpaper-original
RUSTFS_BUCKET_PREVIEW=wallpaper-preview
RUSTFS_BUCKET_THUMBNAIL=wallpaper-thumbnail
RUSTFS_BUCKET_WATERMARK=wallpaper-watermark
RUSTFS_BUCKET_AUDIT=wallpaper-audit

APP_BOOTSTRAP_ADMIN_PASSWORD=$admin_password
APP_BOOTSTRAP_DEMO_USERS_ENABLED=$demo_users_enabled

APP_ALLOWED_ORIGINS=$allowed_origins
APP_SECURITY_DEVELOPMENT_TOKEN=$dev_token
APP_SECURITY_DEVELOPMENT_TOKEN_ENABLED=false
APP_SECURITY_JWT_SECRET=$jwt_secret
APP_SECURITY_ACCESS_TOKEN_TTL=15m
APP_SECURITY_REFRESH_TOKEN_TTL=7d

APP_MAIL_ENABLED=false
APP_MAIL_HOST=$mail_host
APP_MAIL_PORT=$mail_port
APP_MAIL_USERNAME=
APP_MAIL_PASSWORD=
APP_MAIL_SMTP_AUTH=false
APP_MAIL_SMTP_STARTTLS=false
APP_MAIL_SMTP_SSL=false
APP_MAIL_FROM=no-reply@example.local
APP_MAIL_FRONTEND_BASE_URL=$frontend_base_url
APP_MAIL_PASSWORD_RESET_TOKEN_TTL=30m

UPLOAD_MAX_FILE_SIZE=50MB
UPLOAD_MAX_REQUEST_SIZE=500MB
EOF
}

mkdir -p "$COMPOSE_DIR"
clear_error_log

require_command docker
run_quiet "Docker Compose v2 不可用" "请确认 docker compose version 可以正常运行；本脚本不支持旧版 docker-compose 命令。" docker compose version
run_quiet "Docker 已安装，但当前无法连接 Docker 服务" "请先启动 Docker Desktop；Linux 服务器请检查 Docker 服务是否启动，以及当前用户是否有 Docker 权限。" docker info

if [ ! -f "$ENV_FILE" ] || [ "$force_env" = true ]; then
	write_env_file
	echo "Generated $ENV_FILE"
elif grep -q "change-me" "$ENV_FILE"; then
	fail "ops/docker/.env 仍包含 change-me 占位值" "请备份需要保留的配置后，使用 --force-env 重新生成安全配置。"
else
	echo "Using existing $ENV_FILE"
fi

if [ "$use_prebuilt" = true ] && grep -Eq '^(BACKEND_IMAGE=wallpaper/backend:local|FRONTEND_IMAGE=wallpaper/frontend:local)' "$ENV_FILE"; then
	fail "当前 .env 仍指向本地镜像名" "请将 BACKEND_IMAGE/FRONTEND_IMAGE 改为已发布镜像，或使用 --force-env 重新生成。"
fi

cd "$COMPOSE_DIR"

compose_files="-f compose.yaml"
if [ "$use_prebuilt" != true ]; then
	compose_files="$compose_files -f compose.build.yaml"
fi
if [ "$mode" = "local" ]; then
	compose_files="$compose_files -f compose.local.yaml"
elif [ "$https" != true ]; then
	compose_files="$compose_files -f compose.web.yaml"
fi
if [ "$https" = true ]; then
	compose_files="$compose_files -f compose.https.yaml"
fi
if [ "$use_prebuilt" = true ]; then
	compose_files="$compose_files -f compose.release.yaml"
fi

profiles=
if [ "$mailpit" = true ]; then
	profiles="$profiles --profile local-mail"
fi

compose_cmd="docker compose -p $project --env-file .env $compose_files $profiles"

echo "Validating Docker Compose configuration..."
run_quiet_eval "Docker Compose 配置校验失败" "请检查 ops/docker/.env 和 Compose 文件；如果是端口、域名或镜像名问题，详情日志中会有 Docker 给出的原因。" "$compose_cmd config"

admin_password=$(get_env_value APP_BOOTSTRAP_ADMIN_PASSWORD)

if [ "$dry_run" = true ]; then
	echo "Dry run complete. No containers were started."
	exit 0
fi

if [ "$use_prebuilt" = true ]; then
	echo "Pulling prebuilt backend/frontend images..."
	run_quiet_eval "预构建镜像拉取失败" "请检查网络、镜像仓库地址、镜像权限和 IMAGE_TAG；详情见日志。" "$compose_cmd pull backend frontend"
	build_arg=
elif [ "$skip_build" = true ]; then
	build_arg=
else
	build_arg="--build"
fi

echo "Starting containers..."
run_quiet_eval "Docker 启动失败" "请检查端口是否被占用、镜像是否可拉取、磁盘空间是否充足；详情见日志。" "$compose_cmd up -d $build_arg"

wait_for_service() {
	service="$1"
	timeout="${2:-360}"
	elapsed=0
	while [ "$elapsed" -lt "$timeout" ]; do
		cid=$(eval "$compose_cmd ps -q $service" 2>/dev/null || true)
		if [ -n "$cid" ]; then
			status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || true)
			if [ "$status" = "healthy" ] || [ "$status" = "running" ]; then
				return 0
			fi
		fi
		sleep 5
		elapsed=$((elapsed + 5))
	done
	return 1
}

echo "Waiting for frontend health..."
if ! wait_for_service frontend 420; then
	{
		echo "Command: $compose_cmd ps"
		sh -c "$compose_cmd ps" || true
		echo
		echo "Command: $compose_cmd logs --tail=120 backend frontend"
		sh -c "$compose_cmd logs --tail=120 backend frontend" || true
	} > "$ERROR_LOG" 2>&1
	fail "服务启动后健康检查超时" "请运行 docker compose ps 查看状态，并查看 backend/frontend 日志；常见原因是端口占用、数据库未就绪或应用配置错误。"
fi

if [ "$https" = true ]; then
	if ! wait_for_service caddy 120; then
		{
			echo "Command: $compose_cmd ps"
			sh -c "$compose_cmd ps" || true
			echo
			echo "Command: $compose_cmd logs --tail=120 caddy"
			sh -c "$compose_cmd logs --tail=120 caddy" || true
		} > "$ERROR_LOG" 2>&1
		fail "HTTPS 入口启动后健康检查超时" "请确认域名已解析到本机公网 IP，且 80/443 端口已开放；详情见日志。"
	fi
	access_url="https://$domain"
elif [ -n "$domain" ]; then
	access_url=$(url_with_port http "$domain" "$frontend_port")
else
	access_url=$(url_with_port http localhost "$frontend_port")
fi

echo
echo "Deployment is ready."
echo "URL: $access_url"
echo "Admin username: admin"
if [ -n "$admin_password" ]; then
	echo "Initial admin password: $admin_password"
else
	echo "Initial admin password: unchanged; set APP_BOOTSTRAP_ADMIN_PASSWORD before the first production boot if needed."
fi
echo "Mail password reset is disabled by default. Configure SMTP and enable it in System Settings when ready."
