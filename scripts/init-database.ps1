# 初始化数据库：导入 database/schema.sql 与 database/seed.sql
# 用法：pwsh -File scripts\init-database.ps1 [-Server localhost] [-Port 3306] [-User root] [-Password 123456] [-DryRun]
param(
    [string]$Server = 'localhost',
    [int]$Port = 3306,
    [string]$User = 'root',
    [string]$Password = '',
    [string]$SchemaFile = '',
    [string]$SeedFile = '',
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$repo = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
if (-not $SchemaFile) { $SchemaFile = Join-Path $repo 'database\schema.sql' }
if (-not $SeedFile) { $SeedFile = Join-Path $repo 'database\seed.sql' }

$mysql = Get-Command mysql -ErrorAction SilentlyContinue
if (-not $mysql) {
    Write-Host '找不到 mysql 客户端，请安装 MySQL 命令行工具或把它加入 PATH。' -ForegroundColor Yellow
    exit 1
}
foreach ($file in @($SchemaFile, $SeedFile)) {
    if (-not (Test-Path $file)) {
        Write-Host "找不到 SQL 文件：$file" -ForegroundColor Yellow
        exit 1
    }
}

if (-not $DryRun -and -not $Password) {
    $secure = Read-Host "请输入 MySQL 用户 $User 的密码（无密码直接回车）" -AsSecureString
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        $Password = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

Write-Host ("== 初始化数据库 {0}:{1} ==" -f $Server, $Port) -ForegroundColor Cyan
$env:MYSQL_PWD = $Password
try {
    foreach ($step in @(
            @{ Name = 'schema.sql'; File = $SchemaFile },
            @{ Name = 'seed.sql'; File = $SeedFile })) {
        $commandLine = 'mysql -h {0} -P {1} -u {2} --default-character-set=utf8mb4 < "{3}"' -f $Server, $Port, $User, $step.File
        Write-Host ("-> {0}" -f $step.Name)
        if ($DryRun) {
            Write-Host ("   {0}" -f $commandLine) -ForegroundColor DarkGray
            continue
        }
        cmd /c $commandLine
        if ($LASTEXITCODE -ne 0) {
            throw "导入 $($step.Name) 失败（mysql exit $LASTEXITCODE）"
        }
    }

    if ($DryRun) {
        Write-Host '（试运行，未执行导入）'
        exit 0
    }

    $verifyLine = 'mysql -h {0} -P {1} -u {2} -N -e "SELECT COUNT(*) FROM balance_check.sys_user"' -f $Server, $Port, $User
    $count = (cmd /c $verifyLine | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) {
        Write-Host '导入完成，但校验查询失败，请手工确认数据库内容。' -ForegroundColor Yellow
        exit 1
    }
    Write-Host ("导入完成：sys_user 共 {0} 行" -f $count) -ForegroundColor Green
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}
