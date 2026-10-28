# 构建交付包：后端 jar + 前端 dist + 数据库脚本 + 文档 + 运维脚本
# 用法：pwsh -File scripts\package-release.ps1 [-Version 0.1.0] [-SkipFrontend] [-NoZip] [-DryRun]
param(
    [string]$Version = '',
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$OutputRoot = '',
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$NoZip,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$repo = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
if (-not $OutputRoot) { $OutputRoot = Join-Path $repo 'dist' }

if (-not $Version) {
    $pom = Get-Content (Join-Path $repo 'backend\pom.xml') -Raw
    $match = [regex]::Match($pom, '<artifactId>balance-check</artifactId>\s*<version>([^<]+)</version>')
    $Version = if ($match.Success) { $match.Groups[1].Value.Trim() } else { '0.0.0' }
}

$releaseName = "balance-check-$Version"
$target = Join-Path $OutputRoot $releaseName

Write-Host ("== 打包 {0} ==" -f $releaseName) -ForegroundColor Cyan
Write-Host ("输出目录：{0}" -f $target)

if ($DryRun) {
    Write-Host '[1/6] 构建后端：backend\mvnw.cmd -o -DskipTests package' -ForegroundColor DarkGray
    Write-Host '[2/6] 构建前端：frontend 目录执行 npm run build' -ForegroundColor DarkGray
    Write-Host '[3/6] 复制 jar 与前端静态资源' -ForegroundColor DarkGray
    Write-Host '[4/6] 复制 database、docs、scripts 与 README' -ForegroundColor DarkGray
    Write-Host '[5/6] 生成 VERSION.txt' -ForegroundColor DarkGray
    Write-Host '[6/6] 压缩为 zip' -ForegroundColor DarkGray
    Write-Host '（试运行，未执行构建）'
    exit 0
}

if (Test-Path $target) { Remove-Item -LiteralPath $target -Recurse -Force }
New-Item -ItemType Directory -Path $target -Force | Out-Null

if (-not $SkipBackend) {
    Write-Host '[1/6] 构建后端…'
    $mvnJavaHome = $JavaHome
    if ($mvnJavaHome -and -not (Test-Path (Join-Path $mvnJavaHome 'bin\java.exe'))) { $mvnJavaHome = '' }
    Push-Location (Join-Path $repo 'backend')
    try {
        if ($mvnJavaHome) { $env:JAVA_HOME = $mvnJavaHome }
        cmd /c '.\mvnw.cmd -o -DskipTests package'
        if ($LASTEXITCODE -ne 0) { throw "后端构建失败（mvnw exit $LASTEXITCODE）" }
    } finally {
        Pop-Location
    }
}

if (-not $SkipFrontend) {
    Write-Host '[2/6] 构建前端…'
    if (-not (Test-Path (Join-Path $repo 'frontend\node_modules'))) {
        throw '前端依赖未安装：请先在 frontend 目录执行 npm install（内网可用 npm install --offline）'
    }
    Push-Location (Join-Path $repo 'frontend')
    try {
        cmd /c 'npm run build'
        if ($LASTEXITCODE -ne 0) { throw "前端构建失败（npm exit $LASTEXITCODE）" }
    } finally {
        Pop-Location
    }
}

Write-Host '[3/6] 复制后端与前端产物…'
if (-not $SkipBackend) {
    $jar = Get-ChildItem (Join-Path $repo 'backend\target') -Filter '*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1
    if (-not $jar) { throw '未找到后端 jar，请确认 package 是否成功' }
    New-Item -ItemType Directory -Path (Join-Path $target 'backend') -Force | Out-Null
    Copy-Item $jar.FullName (Join-Path $target 'backend\balance-check.jar')
}
if (-not $SkipFrontend) {
    Copy-Item (Join-Path $repo 'frontend\dist') (Join-Path $target 'frontend') -Recurse
}

Write-Host '[4/6] 复制数据库脚本、文档、脚本与 README…'
$databaseTarget = Join-Path $target 'database'
New-Item -ItemType Directory -Path $databaseTarget -Force | Out-Null
Copy-Item (Join-Path $repo 'database\*.sql') $databaseTarget
Copy-Item (Join-Path $repo 'docs') (Join-Path $target 'docs') -Recurse
$scriptsTarget = Join-Path $target 'scripts'
New-Item -ItemType Directory -Path $scriptsTarget -Force | Out-Null
Copy-Item (Join-Path $repo 'scripts\*.ps1') $scriptsTarget
Copy-Item (Join-Path $repo 'scripts\*.java') $scriptsTarget -ErrorAction SilentlyContinue
Copy-Item (Join-Path $repo 'README.md') (Join-Path $target 'README.md')

Write-Host '[5/6] 生成 VERSION.txt…'
$gitCommit = 'unknown'
try { $gitCommit = (git -C $repo rev-parse --short HEAD) } catch { }
$jdkVersion = 'unknown'
try {
    $javaExe = if ($JavaHome) { Join-Path $JavaHome 'bin\java.exe' } else { 'java' }
    $jdkVersion = ((& $javaExe -version 2>&1 | Out-String) -split "\r?\n")[0].Trim()
} catch { }
$nodeVersion = '未安装'
try { $nodeVersion = (& node -v) } catch { }
$lines = @(
    '名称: 天平校验视频分析系统（balance-check）',
    "版本: $Version",
    "构建时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
    "构建主机: $env:COMPUTERNAME",
    "Git 提交: $gitCommit",
    "JDK: $jdkVersion",
    "Node.js: $nodeVersion"
)
$lines | Set-Content (Join-Path $target 'VERSION.txt') -Encoding UTF8

if (-not $NoZip) {
    Write-Host '[6/6] 压缩交付包…'
    $zipPath = Join-Path $OutputRoot ("{0}.zip" -f $releaseName)
    if (Test-Path $zipPath) { Remove-Item -LiteralPath $zipPath -Force }
    Compress-Archive -Path $target -DestinationPath $zipPath -Force
    Write-Host ("已生成 {0}" -f $zipPath) -ForegroundColor Green
}

$fileCount = @(Get-ChildItem $target -Recurse -File).Count
$sizeMb = [math]::Round((Get-ChildItem $target -Recurse -File | Measure-Object -Property Length -Sum).Sum / 1MB, 1)
Write-Host ""
Write-Host ("交付包完成：{0}（{1} 个文件，{2} MB）" -f $target, $fileCount, $sizeMb) -ForegroundColor Green
Write-Host ('校验命令：pwsh -File scripts\verify-release.ps1 -Path "{0}"' -f $target)
