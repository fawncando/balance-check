# 启动后端：默认使用交付包或开发目录中的 jar，可覆盖数据库与存储配置
# 用法：pwsh -File scripts\start-backend.ps1 [-Port 8081] [-DryRun]
#       pwsh -File scripts\start-backend.ps1 -DbPassword 123456 -UploadDir D:\data\uploads
param(
    [string]$Jar = '',
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$UploadDir = '',
    [string]$DbUrl = '',
    [string]$DbUsername = '',
    [string]$DbPassword = '',
    [int]$Port = 8081,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$root = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }

if (-not $Jar) {
    $releaseJar = Join-Path $root 'backend\balance-check.jar'
    if (Test-Path $releaseJar) {
        $Jar = $releaseJar
    } else {
        $candidate = Get-ChildItem (Join-Path $root 'backend\target') -Filter '*.jar' -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1
        if ($candidate) { $Jar = $candidate.FullName }
    }
}
if (-not $Jar -or -not (Test-Path $Jar)) {
    Write-Host '找不到后端 jar：请先在 backend 目录执行 .\mvnw.cmd -o -DskipTests package，或用 -Jar 指定路径。' -ForegroundColor Yellow
    exit 1
}

$java = ''
if ($JavaHome) {
    $candidate = Join-Path $JavaHome 'bin\java.exe'
    if (Test-Path $candidate) { $java = $candidate }
}
if (-not $java) {
    $command = Get-Command java -ErrorAction SilentlyContinue
    if ($command) { $java = $command.Source }
}
if (-not $java) {
    Write-Host '找不到 java，请安装 JDK 17 及以上，或设置 JAVA_HOME。' -ForegroundColor Yellow
    exit 1
}

$logDir = Join-Path $root 'logs'
if ($DbUrl) { $env:DB_URL = $DbUrl }
if ($DbUsername) { $env:DB_USERNAME = $DbUsername }
if ($DbPassword) { $env:DB_PASSWORD = $DbPassword }
if ($UploadDir) { $env:APP_UPLOAD_DIR = $UploadDir }
$env:SERVER_PORT = [string]$Port

$argLine = '-jar "' + $Jar + '" --server.port=' + $Port
$url = "http://localhost:$Port"

if ($DryRun) {
    Write-Host '== 启动参数（试运行）==' -ForegroundColor Cyan
    Write-Host ("java       : {0}" -f $java)
    Write-Host ("jar        : {0}" -f $Jar)
    Write-Host ("端口        : {0}" -f $Port)
    Write-Host ("上传目录    : {0}" -f ($(if ($env:APP_UPLOAD_DIR) { $env:APP_UPLOAD_DIR } else { '(application.yml 默认值)' })))
    Write-Host ("数据库 URL  : {0}" -f ($(if ($env:DB_URL) { $env:DB_URL } else { '(application.yml 默认值)' })))
    Write-Host ("数据库用户  : {0}" -f ($(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { '(application.yml 默认值)' })))
    Write-Host ("数据库口令  : {0}" -f ($(if ($DbPassword) { '******' } else { '(application.yml 默认值)' })))
    Write-Host ("启动命令    : {0} {1}" -f $java, $argLine)
    Write-Host '（试运行，未启动进程）'
    exit 0
}

New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outLog = Join-Path $logDir ("backend-{0}.log" -f $stamp)
$errLog = Join-Path $logDir ("backend-{0}.err.log" -f $stamp)

$process = Start-Process -FilePath $java -ArgumentList $argLine -WorkingDirectory $root -PassThru -WindowStyle Hidden -RedirectStandardOutput $outLog -RedirectStandardError $errLog
$process.Id | Set-Content (Join-Path $logDir 'backend.pid') -Encoding ASCII

Write-Host ("后端已启动，进程号 {0}" -f $process.Id) -ForegroundColor Green
Write-Host ("日志：{0}" -f $outLog)

$deadline = (Get-Date).AddSeconds(40)
$up = $false
while ((Get-Date) -lt $deadline) {
    if ($process.HasExited) { break }
    try {
        $response = Invoke-WebRequest "$url/api/system/health" -UseBasicParsing -TimeoutSec 2
        if ($response.StatusCode -eq 200) { $up = $true; break }
    } catch { }
    Start-Sleep -Milliseconds 800
}

if ($up) {
    Write-Host ("健康检查通过：{0}/api/system/health" -f $url) -ForegroundColor Green
    Write-Host ("浏览器访问：{0}" -f $url)
} else {
    Write-Host '启动后 40 秒内健康检查未通过，请查看日志排查（常见原因：数据库未启动、口令不对、端口占用）。' -ForegroundColor Yellow
    Get-Content $errLog -ErrorAction SilentlyContinue -Tail 10 | ForEach-Object { Write-Host ("  {0}" -f $_) -ForegroundColor DarkGray }
    exit 1
}
