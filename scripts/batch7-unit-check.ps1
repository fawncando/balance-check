# 批次 7 独立校验：天平清单校验规则（BalanceRulesCheck）
# 不依赖数据库与 Maven 测试插件；需先编译后端：cd backend; .\mvnw.cmd -o -DskipTests compile
# 用法：pwsh -File scripts\batch7-unit-check.ps1 [-JavaHome <JDK 目录>] [-Classes <后端编译产物>]
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
    $jarTool = Join-Path $JavaHome 'bin\jar.exe'
} else {
    $javacCommand = Get-Command javac -ErrorAction SilentlyContinue
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if (-not $javacCommand -or -not $javaCommand) {
        Write-Host '找不到 javac/java，请安装 JDK 17 及以上，或用 -JavaHome 指定 JDK 目录。' -ForegroundColor Yellow
        exit 1
    }
    $javac = $javacCommand.Source
    $java = $javaCommand.Source
    $jarTool = (Get-Command jar -ErrorAction SilentlyContinue).Source
}
if (-not $jarTool -or -not (Test-Path $jarTool)) {
    Write-Host '找不到 jar 工具，请使用完整 JDK。' -ForegroundColor Yellow
    exit 1
}

$ruleClass = Join-Path $Classes 'com\bzd\balance\balance\BalanceRules.class'
if (-not (Test-Path $ruleClass)) {
    Write-Host "找不到后端编译产物：$ruleClass" -ForegroundColor Yellow
    Write-Host "请先执行：cd backend; .\mvnw.cmd -o -DskipTests compile" -ForegroundColor Yellow
    exit 1
}

$out = Join-Path $repo 'backend\target\unit-check'
New-Item -ItemType Directory -Path $out -Force | Out-Null
$classJar = Join-Path $out 'backend-classes.jar'

Write-Host "== 批次 7 独立校验 ==" -ForegroundColor Cyan

& $jarTool cf $classJar -C $Classes .
$compileOutput = & $javac -encoding UTF-8 -cp $classJar -d $out (Join-Path $repo 'scripts\BalanceRulesCheck.java') 2>&1
if ($LASTEXITCODE -ne 0) { $compileOutput | Write-Host; exit $LASTEXITCODE }

& $java "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -cp "$classJar;$out" BalanceRulesCheck
exit $LASTEXITCODE
