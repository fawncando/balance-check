# 并发识别、提交与复核压力测试。请使用独立测试库运行。
# 用法：pwsh -File scripts\stress-test.ps1 -BaseUrl http://localhost:8081 -VideoDir D:\videos
param(
    [string]$BaseUrl = 'http://localhost:8081',
    [string]$VideoDir = 'C:\Users\fawn\Downloads',
    [string]$OutputJson = ''
)

$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')
$repo = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
$runtimeEnv = Join-Path $repo '.local\runtime.env.ps1'
if (Test-Path -LiteralPath $runtimeEnv) {
    . $runtimeEnv
}
$workerPath = Join-Path $PSScriptRoot 'stress-worker.ps1'
$cases = @(
    @{ username = 'liyi'; balance = 'F001'; video = 'Balance_2.mp4' },
    @{ username = 'lier'; balance = 'F002'; video = 'Balance_1_first.mp4' },
    @{ username = 'liyi'; balance = 'F002'; video = 'Balance_1_second.mp4' }
)

foreach ($case in $cases) {
    $path = Join-Path $VideoDir $case.video
    if (-not (Test-Path -LiteralPath $path)) {
        throw "缺少压力测试视频：$path"
    }
}

Write-Host "== 并发压力测试 $base ==" -ForegroundColor Cyan
$started = Get-Date
$recognitionJobs = @()
foreach ($case in $cases) {
    $recognitionJobs += Start-Job -ScriptBlock {
        param($workerPath, $case, $base, $videoDir)
        & $workerPath -Mode recognition -Case $case -BaseUrl $base -VideoDir $videoDir
    } -ArgumentList $workerPath, $case, $base, $VideoDir
}
$recognition = @($recognitionJobs | Wait-Job | Receive-Job)
$recognitionJobs | Remove-Job -Force

$recordJobs = @()
for ($i = 1; $i -le 6; $i++) {
    $code = if ($i % 2) { 'F001' } else { 'F002' }
    $recordJobs += Start-Job -ScriptBlock {
        param($workerPath, $index, $base, $code)
        & $workerPath -Mode record -Index $index -BaseUrl $base -BalanceCode $code
    } -ArgumentList $workerPath, $i, $base, $code
}
$records = @($recordJobs | Wait-Job | Receive-Job)
$recordJobs | Remove-Job -Force

$elapsed = [math]::Round(((Get-Date) - $started).TotalSeconds, 1)
$result = [pscustomobject]@{
    elapsedSeconds = $elapsed
    recognition = $recognition
    records = $records
    recognitionPassed = @($recognition | Where-Object {
        $_.status -eq 'ANALYZED' -and $_.calibrationId
    }).Count
    recordsPassed = @($records | Where-Object { $_.status -eq 'REVIEWED' }).Count
}
$json = $result | ConvertTo-Json -Depth 8
if ($OutputJson) {
    $outputDir = Split-Path -Parent $OutputJson
    if ($outputDir) {
        New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
    }
    $json | Set-Content -LiteralPath $OutputJson -Encoding UTF8
}
$json

if ($result.recognitionPassed -ne 3 -or $result.recordsPassed -ne 6) {
    exit 1
}
exit 0
