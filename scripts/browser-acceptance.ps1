# 通过本机 Edge DevTools 协议执行真实浏览器验收并生成截图。
# 前置条件：Edge 以 --remote-debugging-port=<Port> 启动，前端 5173、后端 8081 已运行。
param(
    [int]$Port = 9222,
    [string]$AppUrl = 'http://localhost:5173',
    [string]$VideoPath = 'C:\Users\fawn\Downloads\Balance_1_second.mp4',
    [string]$ScreenshotDir = ''
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'test-credentials.ps1')
$repo = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
if (-not $ScreenshotDir) { $ScreenshotDir = Join-Path $repo 'docs\screenshots' }
New-Item -ItemType Directory -Path $ScreenshotDir -Force | Out-Null
if (-not (Test-Path -LiteralPath $VideoPath)) {
    throw "视频不存在：$VideoPath"
}

$tabs = Invoke-RestMethod "http://localhost:$Port/json"
$tab = $tabs | Where-Object { $_.url -like "$AppUrl*" } | Select-Object -First 1
if (-not $tab) {
    $tab = Invoke-RestMethod "http://localhost:$Port/json/new?$AppUrl" -Method Put
}

$ws = [System.Net.WebSockets.ClientWebSocket]::new()
$token = [System.Threading.CancellationToken]::None
$ws.ConnectAsync([Uri]$tab.webSocketDebuggerUrl, $token).GetAwaiter().GetResult()
$script:cdpId = 0

function Invoke-Cdp([string]$method, [hashtable]$params = @{}) {
    $script:cdpId++
    $id = $script:cdpId
    $payload = @{ id = $id; method = $method; params = $params } | ConvertTo-Json -Depth 20 -Compress
    $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
    $segment = [ArraySegment[byte]]::new($bytes)
    $ws.SendAsync($segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $token).GetAwaiter().GetResult()

    while ($true) {
        $text = [Text.StringBuilder]::new()
        do {
            $buffer = New-Object byte[] 65536
            $receive = [ArraySegment[byte]]::new($buffer)
            $result = $ws.ReceiveAsync($receive, $token).GetAwaiter().GetResult()
            [void]$text.Append([Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count))
        } while (-not $result.EndOfMessage)
        $message = $text.ToString() | ConvertFrom-Json
        if ($null -ne $message.id -and $message.id -eq $id) {
            if ($message.error) {
                throw ($message.error | ConvertTo-Json -Compress)
            }
            return $message.result
        }
    }
}

function Invoke-PageJs([string]$expression) {
    $result = Invoke-Cdp 'Runtime.evaluate' @{
        expression = $expression
        awaitPromise = $true
        returnByValue = $true
    }
    if ($result.exceptionDetails) {
        throw $result.exceptionDetails.text
    }
    return $result.result.value
}

function Wait-Page([string]$expression, [int]$seconds = 30) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            if (Invoke-PageJs $expression) { return }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    throw "等待页面条件超时：$expression"
}

function Save-Screenshot([string]$name) {
    $shot = Invoke-Cdp 'Page.captureScreenshot' @{
        format = 'png'
        fromSurface = $true
        captureBeyondViewport = $true
    }
    [IO.File]::WriteAllBytes((Join-Path $ScreenshotDir $name), [Convert]::FromBase64String($shot.data))
    Write-Host "截图：$(Join-Path $ScreenshotDir $name)"
}

try {
    Invoke-Cdp 'Page.enable' | Out-Null
    Invoke-Cdp 'Runtime.enable' | Out-Null
    Invoke-Cdp 'DOM.enable' | Out-Null
    Invoke-Cdp 'Emulation.setDeviceMetricsOverride' @{
        width = 1440
        height = 1000
        deviceScaleFactor = 1
        mobile = $false
    } | Out-Null

    Invoke-Cdp 'Page.navigate' @{ url = "$AppUrl/login" } | Out-Null
    Wait-Page "document.body && document.body.innerText.includes('天平校验视频分析系统')"
    Start-Sleep -Milliseconds 500
    Save-Screenshot '01-login.png'

    $null = Invoke-PageJs @"
(() => {
  const inputs = [...document.querySelectorAll('input')];
  const set = (el, value) => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    setter.call(el, value);
    el.dispatchEvent(new Event('input', { bubbles: true }));
  };
  set(inputs.find(i => i.type === 'text'), 'liyi');
  set(inputs.find(i => i.type === 'password'), '$DemoPassword');
  document.querySelector('.login-btn').click();
  return true;
})()
"@
    Wait-Page "location.pathname === '/workbench' && document.body.innerText.includes('检验工作台')"
    Start-Sleep -Milliseconds 700
    Save-Screenshot '02-workbench.png'

    $null = Invoke-PageJs @'
(() => {
  const select = document.querySelector('.pick .el-select');
  if (!select) throw new Error('找不到天平选择框');
  select.click();
  return true;
})()
'@
    Wait-Page "[...document.querySelectorAll('.el-select-dropdown__item')].some(e => e.innerText.includes('F002'))"
    $null = Invoke-PageJs @'
(() => {
  const option = [...document.querySelectorAll('.el-select-dropdown__item')]
    .find(e => e.innerText.includes('F002'));
  option.click();
  return true;
})()
'@
    Wait-Page "document.querySelector('.pick .el-select')?.innerText.includes('F002')"

    $doc = Invoke-Cdp 'DOM.getDocument' @{ depth = 2 }
    $query = Invoke-Cdp 'DOM.querySelector' @{
        nodeId = $doc.root.nodeId
        selector = 'input[type=file]'
    }
    if (-not $query.nodeId) { throw '找不到视频文件输入框' }
    Invoke-Cdp 'DOM.setFileInputFiles' @{
        nodeId = $query.nodeId
        files = @($VideoPath)
    } | Out-Null
    Wait-Page "document.body.innerText.includes('上传并自动识别')"
    Start-Sleep -Milliseconds 500
    Save-Screenshot '03-upload-selected.png'

    $null = Invoke-PageJs @'
(() => {
  const button = [...document.querySelectorAll('button')]
    .find(b => b.innerText.trim() === '上传并自动识别');
  if (!button) throw new Error('找不到上传按钮');
  button.click();
  return true;
})()
'@
    Wait-Page "location.pathname.includes('/calibrations/') && document.body.innerText.includes('编辑校验记录')" 120
    Start-Sleep -Milliseconds 800
    Save-Screenshot '06-calibration-form.png'

    $measurementCount = Invoke-PageJs @'
[...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
  .filter(row => row.innerText.includes(' g')).length
'@
    if ($measurementCount -ne 9) {
        throw "识别草稿不是 9 行，实际 $measurementCount 行"
    }

    $null = Invoke-PageJs @'
(() => {
  const groups = [...document.querySelectorAll('.radios .el-radio-group')];
  if (groups.length < 3) throw new Error('过程信息控件不完整');
  groups[0].querySelectorAll('.el-radio')[0].click();
  groups[1].querySelectorAll('.el-radio')[0].click();
  groups[2].querySelectorAll('.el-radio')[0].click();
  const submit = [...document.querySelectorAll('button')]
    .find(b => b.innerText.trim() === '保存并提交复核');
  if (!submit) throw new Error('找不到提交按钮');
  submit.click();
  return true;
})()
'@
    Wait-Page "location.pathname === '/workbench' && document.body.innerText.includes('待复核')" 30
    Start-Sleep -Milliseconds 700
    Save-Screenshot '07-record-submitted.png'
    Save-Screenshot '05-uploaded.png'

    Invoke-Cdp 'Page.navigate' @{ url = "$AppUrl/records" } | Out-Null
    Wait-Page "location.pathname === '/records' && document.body.innerText.includes('校验记录查询')" 15
    Start-Sleep -Milliseconds 500
    Save-Screenshot '08-calibration-list.png'

    Write-Host '浏览器验收通过：登录、上传、自动识别、九行草稿、提交复核、记录查询。'
} finally {
    try { $ws.Dispose() } catch { }
}
