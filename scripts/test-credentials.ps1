# 演示账号口令：优先取环境变量；未设置时自动加载仓库根的本地文件（.local/runtime.env.ps1，不入库）
if (-not $env:BALANCE_DEMO_PASSWORD) {
    $envFile = Join-Path (Split-Path -Parent $PSScriptRoot) '.local\runtime.env.ps1'
    if (Test-Path $envFile) {
        . $envFile
    }
}
if (-not $env:BALANCE_DEMO_PASSWORD) {
    throw '缺少 BALANCE_DEMO_PASSWORD。请先加载 .local\runtime.env.ps1 或设置该环境变量。'
}

$DemoPassword = $env:BALANCE_DEMO_PASSWORD
