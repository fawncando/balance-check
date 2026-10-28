# 一键测试：后端编译 + 独立校验 + 前端单元测试 + 接口冒烟（后端在线时）
# 用法：pwsh -File scripts\test-all.ps1 [-BaseUrl http://localhost:8081] [-SkipApi] [-SkipBuild]
param(
    [string]$BaseUrl = 'http://localhost:8081',
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$SkipBuild,
    [switch]$SkipApi,
    [switch]$SkipFrontend
)

$ErrorActionPreference = 'Continue'
$base = $BaseUrl.TrimEnd('/')
$repo = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
$hostExe = (Get-Process -Id $PID).Path
$script:results = @()

function Add-Result([string]$name, [bool]$ok, [string]$detail = '') {
    $script:results += [pscustomobject]@{ Name = $name; Ok = $ok; Detail = $detail }
}

function Invoke-Child([string]$name, [string]$scriptPath, [string[]]$scriptArgs = @()) {
    Write-Host ""
    Write-Host ("== {0} ==" -f $name) -ForegroundColor Cyan
    if (-not (Test-Path $scriptPath)) {
        Write-Host ("脚本不存在：{0}" -f $scriptPath) -ForegroundColor Red
        Add-Result $name $false '脚本不存在'
        return
    }
    & $hostExe -NoProfile -ExecutionPolicy Bypass -File $scriptPath @scriptArgs
    $code = $LASTEXITCODE
    Add-Result $name ($code -eq 0) ("exit {0}" -f $code)
}

Write-Host "== 天平校验视频分析系统 一键测试 ==" -ForegroundColor Cyan
Write-Host ("仓库：{0}" -f $repo)

$unitArgs = @()
if ($JavaHome -and (Test-Path (Join-Path $JavaHome 'bin\javac.exe'))) {
    $unitArgs = @('-JavaHome', $JavaHome)
}

if ($SkipBuild) {
    Add-Result '后端编译' $true '跳过'
} else {
    Write-Host ""
    Write-Host '== 后端编译 ==' -ForegroundColor Cyan
    $mvnJavaHome = $JavaHome
    if ($mvnJavaHome -and -not (Test-Path (Join-Path $mvnJavaHome 'bin\java.exe'))) { $mvnJavaHome = '' }
    Push-Location (Join-Path $repo 'backend')
    try {
        if ($mvnJavaHome) { $env:JAVA_HOME = $mvnJavaHome }
        cmd /c '.\mvnw.cmd -o -q -DskipTests compile'
        $buildCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    Add-Result '后端编译' ($buildCode -eq 0) ("exit {0}" -f $buildCode)
}

Invoke-Child '批次 6 独立校验（复核规则、记录表渲染）' (Join-Path $repo 'scripts\batch6-unit-check.ps1') $unitArgs
Invoke-Child '批次 7 独立校验（天平清单规则）' (Join-Path $repo 'scripts\batch7-unit-check.ps1') $unitArgs

if ($SkipFrontend) {
    Add-Result '前端单元测试' $true '跳过'
} else {
    Write-Host ""
    Write-Host '== 前端单元测试 ==' -ForegroundColor Cyan
    if (Get-Command node -ErrorAction SilentlyContinue) {
        & node --test (Join-Path $repo 'frontend/src/utils/judgement.test.mjs') (Join-Path $repo 'frontend/src/utils/review.test.mjs')
        $nodeCode = $LASTEXITCODE
        Add-Result '前端单元测试' ($nodeCode -eq 0) ("exit {0}" -f $nodeCode)
    } else {
        Write-Host '找不到 node，跳过前端单元测试。' -ForegroundColor Yellow
        Add-Result '前端单元测试' $true '跳过（未安装 node）'
    }
}

$health = $false
try {
    $response = Invoke-WebRequest "$base/api/system/health" -UseBasicParsing -TimeoutSec 3
    $health = ($response.StatusCode -eq 200)
} catch {
    $health = $false
}

if ($SkipApi) {
    Add-Result '接口冒烟测试' $true '跳过'
} elseif (-not $health) {
    Write-Host ""
    Write-Host ("后端未启动（{0}），跳过接口冒烟测试。先启动后端再执行可跑完整用例。" -f $base) -ForegroundColor Yellow
    Add-Result '接口冒烟测试' $true '跳过（后端未启动）'
} else {
    Invoke-Child '批次 3 冒烟（登录、权限、天平、上传）' (Join-Path $repo 'scripts\smoke-test.ps1') @('-BaseUrl', $base)
    Invoke-Child '批次 5 冒烟（判定、草稿、提交锁定、双量程）' (Join-Path $repo 'scripts\batch5-test.ps1') @('-BaseUrl', $base)
    Invoke-Child '批次 6 冒烟（复核、退回、解锁、查询、导出）' (Join-Path $repo 'scripts\batch6-test.ps1') @('-BaseUrl', $base)
    Invoke-Child '批次 7 冒烟（清单维护、操作日志、模板）' (Join-Path $repo 'scripts\batch7-test.ps1') @('-BaseUrl', $base)
}

Write-Host ""
Write-Host '== 汇总 ==' -ForegroundColor Cyan
foreach ($item in $script:results) {
    $mark = if (-not $item.Ok) { 'FAIL' } elseif ($item.Detail -like '跳过*') { 'SKIP' } else { 'PASS' }
    $color = switch ($mark) { 'FAIL' { 'Red' } 'SKIP' { 'Yellow' } default { 'Green' } }
    Write-Host ("[{0}] {1}  {2}" -f $mark, $item.Name, $item.Detail) -ForegroundColor $color
}

$failed = @($script:results | Where-Object { -not $_.Ok })
Write-Host ""
Write-Host ("共 {0} 组，失败 {1} 组" -f $script:results.Count, $failed.Count) -ForegroundColor $(if ($failed.Count -eq 0) { 'Green' } else { 'Red' })
exit $(if ($failed.Count -eq 0) { 0 } else { 1 })
