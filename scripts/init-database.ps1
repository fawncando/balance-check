# 初始化数据库：导入 database/schema.sql 与 database/seed.sql
# 用法：pwsh -File scripts\init-database.ps1 -Password <root密码> -AppPassword <应用数据库密码>
param(
    [string]$Server = 'localhost',
    [int]$Port = 3306,
    [string]$User = 'root',
    [string]$Password = '',
    [string]$AppUser = 'balance_app',
    [string]$AppPassword = '',
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
if ($AppUser -notmatch '^[a-z][a-z0-9_]{2,31}$') {
    throw '应用数据库用户名只能包含小写字母、数字和下划线，并以字母开头'
}
if (-not $DryRun -and -not $AppPassword) {
    throw '初始化数据库时必须提供 -AppPassword，不能使用空口令'
}

Write-Host ("== 初始化数据库 {0}:{1} ==" -f $Server, $Port) -ForegroundColor Cyan
$env:MYSQL_PWD = $Password
try {
    $steps = @(
            @{ Name = 'schema.sql'; File = $SchemaFile },
            @{ Name = 'seed.sql'; File = $SeedFile })
    $migrationDir = Join-Path $repo 'database\migrations'
    if (Test-Path $migrationDir) {
        Get-ChildItem $migrationDir -Filter '*.sql' | Sort-Object Name | ForEach-Object {
            $steps += @{ Name = $_.Name; File = $_.FullName }
        }
    }
    foreach ($step in $steps) {
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

    $escapedAppPassword = $AppPassword.Replace("'", "''")
    $sql = @"
CREATE USER IF NOT EXISTS '$AppUser'@'localhost' IDENTIFIED BY '$escapedAppPassword';
ALTER USER '$AppUser'@'localhost' IDENTIFIED BY '$escapedAppPassword';
GRANT SELECT, INSERT, UPDATE, DELETE ON balance_check.* TO '$AppUser'@'localhost';
FLUSH PRIVILEGES;
"@
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $mysql.Source
    $psi.Arguments = "-h `"$Server`" -P $Port -u `"$User`" --default-character-set=utf8mb4"
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true
    $process = [System.Diagnostics.Process]::Start($psi)
    $process.StandardInput.Write($sql)
    $process.StandardInput.Close()
    $appUserError = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw "创建应用数据库账号失败：$appUserError"
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
