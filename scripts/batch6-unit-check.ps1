# 批次 6 独立校验：复核规则（ReviewRulesCheck）与记录表渲染（RecordSheetCheck）
# 不依赖数据库与 Maven 测试插件；需先编译后端：cd backend; .\mvnw.cmd -o -DskipTests compile
# 用法：pwsh -File scripts\batch6-unit-check.ps1 [-JavaHome <JDK 目录>] [-Classes <后端编译产物>]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Classes = ''
)

$ErrorActionPreference = 'Stop'
# 以脚本所在目录的上层（仓库根）为准；被以脚本块方式调用时退回当前目录
$repo = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
if (-not $Classes) { $Classes = Join-Path $repo 'backend\target\classes' }

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

# 优先用 JAVA_HOME，JAVA_HOME 失效时退回 PATH 上的 javac/java
if ($JavaHome -and (Test-Path (Join-Path $JavaHome 'bin\javac.exe'))) {
    $javac = Join-Path $JavaHome 'bin\javac.exe'
    $java = Join-Path $JavaHome 'bin\java.exe'
} else {
    $javacCommand = Get-Command javac -ErrorAction SilentlyContinue
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if (-not $javacCommand -or -not $javaCommand) {
        Write-Host '找不到 javac/java，请安装 JDK 17 及以上，或用 -JavaHome 指定 JDK 目录。' -ForegroundColor Yellow
        exit 1
    }
    $javac = $javacCommand.Source
    $java = $javaCommand.Source
}

$ruleClass = Join-Path $Classes 'com\bzd\balance\calibration\ReviewRules.class'
if (-not (Test-Path $ruleClass)) {
    Write-Host "找不到后端编译产物：$ruleClass" -ForegroundColor Yellow
    Write-Host "请先执行：cd backend; .\mvnw.cmd -o -DskipTests compile" -ForegroundColor Yellow
    exit 1
}

$out = Join-Path $repo 'backend\target\unit-check'
New-Item -ItemType Directory -Path $out -Force | Out-Null

Write-Host "== 批次 6 独立校验 ==" -ForegroundColor Cyan

& $javac -encoding UTF-8 -cp $Classes -d $out (Join-Path $repo 'scripts\ReviewRulesCheck.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $javac -encoding UTF-8 -cp "$Classes;$out" -d $out (Join-Path $repo 'scripts\RecordSheetCheck.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $java "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -cp "$Classes;$out" ReviewRulesCheck
$rulesExit = $LASTEXITCODE

Write-Host ""
& $java "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -cp "$Classes;$out" com.bzd.balance.calibration.RecordSheetCheck
$sheetExit = $LASTEXITCODE

if ($rulesExit -ne 0 -or $sheetExit -ne 0) { exit 1 }
exit 0
