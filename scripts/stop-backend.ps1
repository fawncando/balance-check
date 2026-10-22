# 停止后端：按 logs/backend.pid 记录的进程号结束进程
# 用法：pwsh -File scripts\stop-backend.ps1 [-PidFile logs\backend.pid]
param(
    [string]$PidFile = ''
)

$ErrorActionPreference = 'Stop'
$root = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
if (-not $PidFile) { $PidFile = Join-Path $root 'logs\backend.pid' }

if (-not (Test-Path $PidFile)) {
    Write-Host "找不到进程文件 $PidFile，后端可能没有通过 start-backend.ps1 启动。" -ForegroundColor Yellow
    exit 1
}

$pidText = (Get-Content $PidFile -Raw).Trim()
$process = Get-Process -Id $pidText -ErrorAction SilentlyContinue
if (-not $process) {
    Write-Host "进程 $pidText 已不存在，清理进程文件。" -ForegroundColor Yellow
    Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
    exit 0
}

Stop-Process -Id $process.Id -Force
Start-Sleep -Milliseconds 500
if (Get-Process -Id $process.Id -ErrorAction SilentlyContinue) {
    Write-Host "进程 $($process.Id) 未能结束，请手工处理。" -ForegroundColor Red
    exit 1
}
Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
Write-Host "后端已停止（进程 $($process.Id)）。" -ForegroundColor Green
