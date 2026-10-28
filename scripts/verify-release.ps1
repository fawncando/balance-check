# 交付包校验：检查构建产物、数据库脚本、文档与脚本是否齐全
# 用法：pwsh -File scripts\verify-release.ps1 -Path dist\balance-check-0.1.0-SNAPSHOT [-AllowMissingFrontend]
param(
    [Parameter(Mandatory = $true)][string]$Path,
    [switch]$AllowMissingFrontend
)

$ErrorActionPreference = 'Stop'
$script:pass = 0
$script:fail = 0
$script:warn = 0

function Check([string]$name, [bool]$ok, [string]$detail = '') {
    if ($ok) { $script:pass++; Write-Host ("[PASS] {0}" -f $name) -ForegroundColor Green }
    else { $script:fail++; Write-Host ("[FAIL] {0} {1}" -f $name, $detail) -ForegroundColor Red }
}

function CheckWarn([string]$name, [bool]$ok, [string]$detail = '') {
    if ($ok) { $script:pass++; Write-Host ("[PASS] {0}" -f $name) -ForegroundColor Green }
    else { $script:warn++; Write-Host ("[WARN] {0} {1}" -f $name, $detail) -ForegroundColor Yellow }
}

$resolved = Resolve-Path -LiteralPath $Path -ErrorAction SilentlyContinue
if (-not $resolved) {
    Write-Host "交付目录不存在：$Path" -ForegroundColor Red
    exit 1
}
$root = $resolved.Path
Write-Host "== 校验交付包 $root ==" -ForegroundColor Cyan

Check '版本说明 VERSION.txt' (Test-Path (Join-Path $root 'VERSION.txt'))
Check 'README.md' (Test-Path (Join-Path $root 'README.md'))

$jar = Join-Path $root 'backend\balance-check.jar'
if (Test-Path $jar) {
    $sizeMb = [math]::Round((Get-Item $jar).Length / 1MB, 1)
    Check '后端可执行 jar' ($sizeMb -ge 5) ("{0} MB" -f $sizeMb)
} else {
    Check '后端可执行 jar' $false $jar
}

$indexHtml = Join-Path $root 'frontend\index.html'
if ($AllowMissingFrontend) {
    CheckWarn '前端静态资源' (Test-Path $indexHtml) '按参数允许缺失'
} else {
    Check '前端静态资源 index.html' (Test-Path $indexHtml)
    Check '前端打包目录 assets' (Test-Path (Join-Path $root 'frontend\assets'))
}

Check '建表脚本 schema.sql' (Test-Path (Join-Path $root 'database\schema.sql'))
Check '初始化数据 seed.sql' (Test-Path (Join-Path $root 'database\seed.sql'))

$docs = Join-Path $root 'docs'
$docCount = if (Test-Path $docs) { @(Get-ChildItem $docs -Filter '*.md' -Recurse).Count } else { 0 }
Check '文档目录（至少 3 篇 Markdown）' ($docCount -ge 3) ("{0} 篇" -f $docCount)

$scriptDir = Join-Path $root 'scripts'
$scriptCount = if (Test-Path $scriptDir) { @(Get-ChildItem $scriptDir -Filter '*.ps1').Count } else { 0 }
Check '运维与测试脚本（至少 5 个）' ($scriptCount -ge 5) ("{0} 个" -f $scriptCount)

if (Test-Path (Join-Path $root 'VERSION.txt')) {
    Write-Host ""
    Write-Host "版本信息：" -ForegroundColor DarkGray
    Get-Content (Join-Path $root 'VERSION.txt') | ForEach-Object { Write-Host ("  {0}" -f $_) -ForegroundColor DarkGray }
}

Write-Host ""
Write-Host ("结果：{0} 通过，{1} 警告，{2} 失败" -f $script:pass, $script:warn, $script:fail) -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
exit $(if ($script:fail -eq 0) { 0 } else { 1 })
