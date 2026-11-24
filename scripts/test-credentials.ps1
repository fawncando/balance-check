if (-not $env:BALANCE_DEMO_PASSWORD) {
    throw '缺少 BALANCE_DEMO_PASSWORD。请先加载 .local\runtime.env.ps1 或设置该环境变量。'
}

$DemoPassword = $env:BALANCE_DEMO_PASSWORD
