# 备份 MySQL 数据库，可选包含上传视频目录。
# 用法：pwsh -File scripts\backup.ps1 -Password <数据库口令> -IncludeUploads
param(
    [string]$Server = 'localhost',
    [int]$Port = 3306,
    [string]$Database = 'balance_check',
    [string]$User = 'root',
    [string]$Password = '',
    [string]$OutputRoot = 'D:/develop/balance-check-data/backups',
    [string]$UploadDir = 'D:/develop/balance-check-data/uploads',
    [switch]$IncludeUploads,
    [switch]$NoZip,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$repo = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$target = Join-Path $OutputRoot ("$Database-$stamp")
$dumpFile = Join-Path $target 'database.sql'
$errorFile = Join-Path $target 'mysqldump.err.log'

if ($DryRun) {
    Write-Host '== 备份参数（试运行）==' -ForegroundColor Cyan
    Write-Host "数据库：$Server`:$Port/$Database"
    Write-Host "输出目录：$target"
    Write-Host "上传目录：$(if ($IncludeUploads) { $UploadDir } else { '不包含' })"
    Write-Host "压缩：$(if ($NoZip) { '否' } else { '是' })"
    exit 0
}

$mysqldump = Get-Command mysqldump -ErrorAction SilentlyContinue
if (-not $mysqldump) {
    throw '找不到 mysqldump，请安装 MySQL 客户端或加入 PATH。'
}
if ($IncludeUploads -and -not (Test-Path -LiteralPath $UploadDir)) {
    throw "上传目录不存在：$UploadDir"
}

New-Item -ItemType Directory -Path $target -Force | Out-Null
$previousPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $Password
try {
    $arguments = @(
        '--single-transaction',
        '--routines',
        '--triggers',
        '--default-character-set=utf8mb4',
        '-h', $Server,
        '-P', [string]$Port,
        '-u', $User,
        $Database
    )
    $process = Start-Process -FilePath $mysqldump.Source -ArgumentList $arguments `
        -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput $dumpFile `
        -RedirectStandardError $errorFile
    if ($process.ExitCode -ne 0) {
        throw "mysqldump 失败（exit $($process.ExitCode)），请查看 $errorFile"
    }
} finally {
    if ($null -eq $previousPassword) {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
        $env:MYSQL_PWD = $previousPassword
    }
}

$uploadCopied = $false
if ($IncludeUploads) {
    $uploadTarget = Join-Path $target 'uploads'
    Copy-Item -LiteralPath $UploadDir -Destination $uploadTarget -Recurse -Force
    $uploadCopied = $true
}

$commit = 'unknown'
try { $commit = (git -C $repo rev-parse --short HEAD) } catch { }
$hash = (Get-FileHash -LiteralPath $dumpFile -Algorithm SHA256).Hash
$manifest = @(
    "database=$Database",
    "server=$Server`:$Port",
    "createdAt=$((Get-Date).ToString('yyyy-MM-dd HH:mm:ss'))",
    "gitCommit=$commit",
    "databaseSha256=$hash",
    "includeUploads=$uploadCopied",
    "uploadDir=$UploadDir"
)
$manifest | Set-Content -LiteralPath (Join-Path $target 'MANIFEST.txt') -Encoding UTF8

$zipPath = $null
if (-not $NoZip) {
    $zipPath = "$target.zip"
    Remove-Item -LiteralPath $zipPath -Force -ErrorAction SilentlyContinue
    Compress-Archive -LiteralPath $target -DestinationPath $zipPath -CompressionLevel Optimal
}

Write-Host ("数据库备份完成：{0}" -f $dumpFile) -ForegroundColor Green
Write-Host ("SHA-256：{0}" -f $hash)
if ($uploadCopied) {
    Write-Host ("上传文件已备份：{0}" -f (Join-Path $target 'uploads'))
}
if ($zipPath) {
    Write-Host ("压缩文件：{0}" -f $zipPath)
}
