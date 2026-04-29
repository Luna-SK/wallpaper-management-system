param(
    [ValidateSet("local", "production")]
    [string]$Mode = "local",
    [int]$FrontendPort = 80,
    [int]$BackendPort = 18090,
    [string]$Domain = "",
    [string]$Email = "",
    [switch]$Https,
    [switch]$Mailpit,
    [switch]$UsePrebuiltImages,
    [string]$Project = "wallpaper",
    [switch]$ForceEnv,
    [switch]$DryRun,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ComposeDir = Join-Path $RootDir "ops/docker"
$EnvFile = Join-Path $ComposeDir ".env"
$ErrorLog = Join-Path $ComposeDir "deploy-error.log"

function Clear-DeployErrorLog {
    if (Test-Path $ErrorLog) {
        Remove-Item -Force $ErrorLog -ErrorAction SilentlyContinue
    }
}

function Fail-Friendly {
    param(
        [string]$Reason,
        [string]$Advice,
        [int]$ExitCode = 1
    )
    [Console]::Error.WriteLine("")
    [Console]::Error.WriteLine("部署未完成：$Reason")
    [Console]::Error.WriteLine("建议：$Advice")
    if ((Test-Path $ErrorLog) -and ((Get-Item $ErrorLog).Length -gt 0)) {
        [Console]::Error.WriteLine("详情日志：$ErrorLog")
    }
    exit $ExitCode
}

function Invoke-FriendlyCommand {
    param(
        [string]$Reason,
        [string]$Advice,
        [string]$Executable,
        [string[]]$Arguments
    )
    New-Item -ItemType Directory -Force -Path $ComposeDir *> $null
    "" | Set-Content -Path $ErrorLog -Encoding UTF8
    try {
        & $Executable @Arguments *> $ErrorLog
        $exitCode = $LASTEXITCODE
    } catch {
        $_ | Out-File -FilePath $ErrorLog -Append -Encoding UTF8
        Fail-Friendly -Reason $Reason -Advice $Advice
    }
    if ($exitCode -ne 0) {
        Fail-Friendly -Reason $Reason -Advice $Advice
    }
    Clear-DeployErrorLog
}

function New-RandomSecret {
    param([int]$Length)
    $chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    $bytes = [byte[]]::new($Length)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    $builder = [System.Text.StringBuilder]::new()
    foreach ($byte in $bytes) {
        [void]$builder.Append($chars[$byte % $chars.Length])
    }
    $builder.ToString()
}

function New-UrlWithPort {
    param([string]$Scheme, [string]$HostName, [int]$Port)
    if (($Scheme -eq "http" -and $Port -eq 80) -or ($Scheme -eq "https" -and $Port -eq 443)) {
        return "${Scheme}://${HostName}"
    }
    "${Scheme}://${HostName}:${Port}"
}

function Get-EnvValue {
    param([string]$Key)
    if (-not (Test-Path $EnvFile)) {
        return ""
    }
    $line = Get-Content $EnvFile | Where-Object { $_ -like "$Key=*" } | Select-Object -First 1
    if (-not $line) {
        return ""
    }
    $line.Substring($Key.Length + 1)
}

function Write-EnvFile {
    $imageTag = "local"
    $backendImage = "wallpaper/backend:local"
    $frontendImage = "wallpaper/frontend:local"
    $demoUsersEnabled = "true"
    $mailHost = ""
    $mailPort = "25"
    if ($Mode -eq "production") {
        $demoUsersEnabled = "false"
    }
    if ($Mailpit) {
        $mailHost = "mailpit"
        $mailPort = "1025"
    }
    if ($UsePrebuiltImages) {
        $imageTag = "latest"
        $backendImage = "ghcr.io/luna-sk/wallpaper-management-system/backend:latest"
        $frontendImage = "ghcr.io/luna-sk/wallpaper-management-system/frontend:latest"
    }

    if ($Https) {
        $frontendBaseUrl = "https://$Domain"
    } elseif ($Domain) {
        $frontendBaseUrl = New-UrlWithPort -Scheme "http" -HostName $Domain -Port $FrontendPort
    } else {
        $frontendBaseUrl = New-UrlWithPort -Scheme "http" -HostName "localhost" -Port $FrontendPort
    }
    $allowedOrigins = "$frontendBaseUrl,http://localhost:$FrontendPort,http://127.0.0.1:$FrontendPort"

    $content = @"
DB_NAME=wallpaper
DB_USERNAME=wallpaper
DB_PASSWORD=$(New-RandomSecret 28)
DB_ROOT_PASSWORD=$(New-RandomSecret 32)

IMAGE_TAG=$imageTag
BACKEND_IMAGE=$backendImage
FRONTEND_IMAGE=$frontendImage

MYSQL_PORT=13316
REDIS_PORT=16389
BACKEND_PORT=$BackendPort
FRONTEND_PORT=$FrontendPort
MAILPIT_SMTP_PORT=1025
MAILPIT_WEB_PORT=8025
HTTP_PORT=80
HTTPS_PORT=443
CADDY_DOMAIN=$Domain
CADDY_EMAIL=$Email

REDIS_PASSWORD=$(New-RandomSecret 32)

RUSTFS_API_PORT=19010
RUSTFS_CONSOLE_PORT=19011
RUSTFS_REGION=cn-east-1
RUSTFS_ACCESS_KEY=$(New-RandomSecret 24)
RUSTFS_SECRET_KEY=$(New-RandomSecret 40)
RUSTFS_BUCKET_ORIGINAL=wallpaper-original
RUSTFS_BUCKET_PREVIEW=wallpaper-preview
RUSTFS_BUCKET_THUMBNAIL=wallpaper-thumbnail
RUSTFS_BUCKET_WATERMARK=wallpaper-watermark
RUSTFS_BUCKET_AUDIT=wallpaper-audit

APP_BOOTSTRAP_ADMIN_PASSWORD=$(New-RandomSecret 20)
APP_BOOTSTRAP_DEMO_USERS_ENABLED=$demoUsersEnabled

APP_ALLOWED_ORIGINS=$allowedOrigins
APP_SECURITY_DEVELOPMENT_TOKEN=$(New-RandomSecret 40)
APP_SECURITY_DEVELOPMENT_TOKEN_ENABLED=false
APP_SECURITY_JWT_SECRET=$(New-RandomSecret 64)
APP_SECURITY_ACCESS_TOKEN_TTL=15m
APP_SECURITY_REFRESH_TOKEN_TTL=7d

APP_MAIL_ENABLED=false
APP_MAIL_HOST=$mailHost
APP_MAIL_PORT=$mailPort
APP_MAIL_USERNAME=
APP_MAIL_PASSWORD=
APP_MAIL_SMTP_AUTH=false
APP_MAIL_SMTP_STARTTLS=false
APP_MAIL_SMTP_SSL=false
APP_MAIL_FROM=no-reply@example.local
APP_MAIL_FRONTEND_BASE_URL=$frontendBaseUrl
APP_MAIL_PASSWORD_RESET_TOKEN_TTL=30m

UPLOAD_MAX_FILE_SIZE=50MB
UPLOAD_MAX_REQUEST_SIZE=500MB
"@
    $encoding = New-Object System.Text.UTF8Encoding -ArgumentList $false
    [System.IO.File]::WriteAllText($EnvFile, $content, $encoding)
}

Clear-DeployErrorLog

if ($Https -and (-not $Domain -or -not $Email)) {
    Fail-Friendly -Reason "参数错误：启用 HTTPS 时缺少域名或邮箱" -Advice "请使用：.\scripts\deploy.ps1 -Mode production -Domain example.com -Email admin@example.com -Https" -ExitCode 2
}
if ($Https -and $Mode -ne "production") {
    Fail-Friendly -Reason "参数错误：HTTPS 只支持生产模式" -Advice "请使用 -Mode production，或在本地模式去掉 -Https。" -ExitCode 2
}
if ($Mailpit -and $Mode -ne "local") {
    Fail-Friendly -Reason "参数错误：Mailpit 只用于本地邮件测试" -Advice "请使用 -Mode local -Mailpit；生产模式请配置真实 SMTP 服务。" -ExitCode 2
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Fail-Friendly -Reason "未检测到 Docker" -Advice "请先安装并启动 Docker Desktop。"
}

Invoke-FriendlyCommand -Reason "Docker Compose v2 不可用" -Advice "请确认 docker compose version 可以正常运行；本脚本不支持旧版 docker-compose 命令。" -Executable "docker" -Arguments @("compose", "version")
Invoke-FriendlyCommand -Reason "Docker 已安装，但当前无法连接 Docker 服务" -Advice "请先启动 Docker Desktop；Linux 服务器请检查 Docker 服务是否启动，以及当前用户是否有 Docker 权限。" -Executable "docker" -Arguments @("info")

if (-not (Test-Path $EnvFile) -or $ForceEnv) {
    Write-EnvFile
    Write-Host "Generated $EnvFile"
} elseif ((Get-Content $EnvFile -Raw) -match "change-me") {
    Fail-Friendly -Reason "ops/docker/.env 仍包含 change-me 占位值" -Advice "请备份需要保留的配置后，使用 -ForceEnv 重新生成安全配置。"
} else {
    Write-Host "Using existing $EnvFile"
}

if ($UsePrebuiltImages) {
    $envText = Get-Content $EnvFile -Raw
    if ($envText -match "(?m)^(BACKEND_IMAGE=wallpaper/backend:local|FRONTEND_IMAGE=wallpaper/frontend:local)$") {
        Fail-Friendly -Reason "当前 .env 仍指向本地镜像名" -Advice "请将 BACKEND_IMAGE/FRONTEND_IMAGE 改为已发布镜像，或使用 -ForceEnv 重新生成。"
    }
}

Set-Location $ComposeDir

$composeArgs = @("compose", "-p", $Project, "--env-file", ".env", "-f", "compose.yaml")
if (-not $UsePrebuiltImages) {
    $composeArgs += @("-f", "compose.build.yaml")
}
if ($Mode -eq "local") {
    $composeArgs += @("-f", "compose.local.yaml")
} elseif (-not $Https) {
    $composeArgs += @("-f", "compose.web.yaml")
}
if ($Https) {
    $composeArgs += @("-f", "compose.https.yaml")
}
if ($UsePrebuiltImages) {
    $composeArgs += @("-f", "compose.release.yaml")
}
if ($Mailpit) {
    $composeArgs += @("--profile", "local-mail")
}

Write-Host "Validating Docker Compose configuration..."
Invoke-FriendlyCommand -Reason "Docker Compose 配置校验失败" -Advice "请检查 ops/docker/.env 和 Compose 文件；如果是端口、域名或镜像名问题，详情日志中会有 Docker 给出的原因。" -Executable "docker" -Arguments ($composeArgs + @("config"))

$adminPassword = Get-EnvValue "APP_BOOTSTRAP_ADMIN_PASSWORD"

if ($DryRun) {
    Write-Host "Dry run complete. No containers were started."
    exit 0
}

if ($UsePrebuiltImages) {
    Write-Host "Pulling prebuilt backend/frontend images..."
    Invoke-FriendlyCommand -Reason "预构建镜像拉取失败" -Advice "请检查网络、镜像仓库地址、镜像权限和 IMAGE_TAG；详情见日志。" -Executable "docker" -Arguments ($composeArgs + @("pull", "backend", "frontend"))
    $upArgs = @("up", "-d")
} elseif ($SkipBuild) {
    $upArgs = @("up", "-d")
} else {
    $upArgs = @("up", "-d", "--build")
}

Write-Host "Starting containers..."
Invoke-FriendlyCommand -Reason "Docker 启动失败" -Advice "请检查端口是否被占用、镜像是否可拉取、磁盘空间是否充足；详情见日志。" -Executable "docker" -Arguments ($composeArgs + $upArgs)

function Wait-ServiceHealthy {
    param([string]$Service, [int]$TimeoutSeconds = 420)
    $elapsed = 0
    while ($elapsed -lt $TimeoutSeconds) {
        $cid = & docker @composeArgs ps -q $Service 2>$null
        if ($cid) {
            $status = & docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $cid 2>$null
            if ($status -eq "healthy" -or $status -eq "running") {
                return $true
            }
        }
        Start-Sleep -Seconds 5
        $elapsed += 5
    }
    return $false
}

Write-Host "Waiting for frontend health..."
if (-not (Wait-ServiceHealthy -Service "frontend")) {
    "Command: docker $($composeArgs -join ' ') ps" | Set-Content -Path $ErrorLog -Encoding UTF8
    & docker @composeArgs ps *>> $ErrorLog
    "" | Out-File -FilePath $ErrorLog -Append -Encoding UTF8
    "Command: docker $($composeArgs -join ' ') logs --tail=120 backend frontend" | Out-File -FilePath $ErrorLog -Append -Encoding UTF8
    & docker @composeArgs logs --tail=120 backend frontend *>> $ErrorLog
    Fail-Friendly -Reason "服务启动后健康检查超时" -Advice "请运行 docker compose ps 查看状态，并查看 backend/frontend 日志；常见原因是端口占用、数据库未就绪或应用配置错误。"
}

if ($Https) {
    if (-not (Wait-ServiceHealthy -Service "caddy" -TimeoutSeconds 120)) {
        "Command: docker $($composeArgs -join ' ') ps" | Set-Content -Path $ErrorLog -Encoding UTF8
        & docker @composeArgs ps *>> $ErrorLog
        "" | Out-File -FilePath $ErrorLog -Append -Encoding UTF8
        "Command: docker $($composeArgs -join ' ') logs --tail=120 caddy" | Out-File -FilePath $ErrorLog -Append -Encoding UTF8
        & docker @composeArgs logs --tail=120 caddy *>> $ErrorLog
        Fail-Friendly -Reason "HTTPS 入口启动后健康检查超时" -Advice "请确认域名已解析到本机公网 IP，且 80/443 端口已开放；详情见日志。"
    }
    $accessUrl = "https://$Domain"
} elseif ($Domain) {
    $accessUrl = New-UrlWithPort -Scheme "http" -HostName $Domain -Port $FrontendPort
} else {
    $accessUrl = New-UrlWithPort -Scheme "http" -HostName "localhost" -Port $FrontendPort
}

Write-Host ""
Write-Host "Deployment is ready."
Write-Host "URL: $accessUrl"
Write-Host "Admin username: admin"
if ($adminPassword) {
    Write-Host "Initial admin password: $adminPassword"
} else {
    Write-Host "Initial admin password: unchanged; set APP_BOOTSTRAP_ADMIN_PASSWORD before the first production boot if needed."
}
Write-Host "Mail password reset is disabled by default. Configure SMTP and enable it in System Settings when ready."
