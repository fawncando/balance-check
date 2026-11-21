# 用 Edge 移动视口和局域网地址验证手机上传页。
param(
    [int]$Port = 9222,
    [string]$AppUrl = 'http://10.22.143.89:5175',
    [string]$ApiUrl = 'http://localhost:8081',
    [string]$VideoPath = 'C:\Users\fawn\Downloads\Balance_1_second.mp4',
    [string]$ScreenshotDir = ''
)

$ErrorActionPreference = 'Stop'
$repo = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
if (-not $ScreenshotDir) { $ScreenshotDir = Join-Path $repo 'docs\screenshots' }
New-Item -ItemType Directory -Path $ScreenshotDir -Force | Out-Null
$tabs = Invoke-RestMethod "http://localhost:$Port/json"
$tab = $tabs | Where-Object { $_.url -like "$AppUrl*" } | Select-Object -First 1
if (-not $tab) { $tab = Invoke-RestMethod "http://localhost:$Port/json/new?$AppUrl/mobile" -Method Put }

$ws = [System.Net.WebSockets.ClientWebSocket]::new()
$token = [System.Threading.CancellationToken]::None
$ws.ConnectAsync([Uri]$tab.webSocketDebuggerUrl, $token).GetAwaiter().GetResult()
$script:id = 0

function Cdp([string]$method, [hashtable]$params = @{}) {
    $script:id++
    $current = $script:id
    $payload = @{ id = $current; method = $method; params = $params } |
        ConvertTo-Json -Depth 20 -Compress
    $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
    $ws.SendAsync([ArraySegment[byte]]::new($bytes),
        [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $token).GetAwaiter().GetResult()
    while ($true) {
        $text = [Text.StringBuilder]::new()
        do {
            $buffer = New-Object byte[] 65536
            $result = $ws.ReceiveAsync([ArraySegment[byte]]::new($buffer), $token).GetAwaiter().GetResult()
            [void]$text.Append([Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count))
        } while (-not $result.EndOfMessage)
        $message = $text.ToString() | ConvertFrom-Json
        if ($message.id -eq $current) {
            if ($message.error) { throw ($message.error | ConvertTo-Json -Compress) }
            return $message.result
        }
    }
}

function Eval([string]$expression) {
    $result = Cdp 'Runtime.evaluate' @{
        expression = $expression
        awaitPromise = $true
        returnByValue = $true
    }
    if ($result.exceptionDetails) { throw $result.exceptionDetails.text }
    return $result.result.value
}

function WaitPage([string]$expression, [int]$seconds = 30) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try { if (Eval $expression) { return } } catch { }
        Start-Sleep -Milliseconds 500
    }
    throw "等待移动端页面超时：$expression"
}

function Screenshot([string]$name) {
    $shot = Cdp 'Page.captureScreenshot' @{
        format = 'png'
        fromSurface = $true
        captureBeyondViewport = $true
    }
    [IO.File]::WriteAllBytes((Join-Path $ScreenshotDir $name),
        [Convert]::FromBase64String($shot.data))
    Write-Host "截图：$(Join-Path $ScreenshotDir $name)"
}

try {
    Cdp 'Page.enable' | Out-Null
    Cdp 'Runtime.enable' | Out-Null
    Cdp 'DOM.enable' | Out-Null
    Cdp 'Network.enable' | Out-Null
    Cdp 'Network.clearBrowserCookies' | Out-Null
    $login = @{ username = 'liyi'; password = '123456' } | ConvertTo-Json -Compress
    $web = $null
    Invoke-WebRequest "$ApiUrl/api/auth/login" -Method Post `
        -ContentType 'application/json' -Body $login -SessionVariable web | Out-Null
    $sessionCookie = $web.Cookies.GetCookies([Uri]$ApiUrl)['JSESSIONID']
    if (-not $sessionCookie) { throw '无法取得移动验收登录会话' }
    $targetUri = [Uri]$AppUrl
    Cdp 'Network.setCookie' @{
        name = 'JSESSIONID'
        value = $sessionCookie.Value
        domain = $targetUri.Host
        path = '/'
    } | Out-Null
    Cdp 'Emulation.setDeviceMetricsOverride' @{
        width = 390
        height = 844
        deviceScaleFactor = 2
        mobile = $true
    } | Out-Null
    Cdp 'Page.navigate' @{ url = "$AppUrl/mobile" } | Out-Null
    WaitPage "location.pathname === '/login' || location.pathname === '/mobile'" 15

    if (Eval "location.pathname === '/login'") {
        $null = Eval @'
(() => {
  const inputs = [...document.querySelectorAll('input')];
  const set = (el, value) => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    setter.call(el, value);
    el.dispatchEvent(new Event('input', { bubbles: true }));
  };
  set(inputs.find(i => i.type === 'text'), 'liyi');
  set(inputs.find(i => i.type === 'password'), '123456');
  return true;
})()
'@
        Start-Sleep -Milliseconds 400
        $buttonPoint = Eval @'
(() => {
  const rect = document.querySelector('.login-btn').getBoundingClientRect();
  return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
})()
'@
        Cdp 'Input.dispatchMouseEvent' @{
            type = 'mousePressed'
            x = $buttonPoint.x
            y = $buttonPoint.y
            button = 'left'
            clickCount = 1
        } | Out-Null
        Cdp 'Input.dispatchMouseEvent' @{
            type = 'mouseReleased'
            x = $buttonPoint.x
            y = $buttonPoint.y
            button = 'left'
            clickCount = 1
        } | Out-Null
        Start-Sleep -Seconds 2
        if (Eval "location.pathname === '/login'") {
            $loginBody = '{"username":"liyi","password":"123456"}'
            $loginResult = Eval @"
fetch('/api/auth/login', {
  method: 'POST',
  credentials: 'include',
  headers: {'Content-Type': 'application/json'},
  body: '$loginBody'
}).then(async response => ({ok: response.ok, status: response.status, text: await response.text()}))
"@
            if (-not $loginResult.ok) {
                throw "移动端登录接口失败：$($loginResult.status) $($loginResult.text)"
            }
            $meResult = Eval @'
fetch('/api/auth/me', {credentials: 'include'})
  .then(async response => ({ok: response.ok, status: response.status, text: await response.text()}))
'@
            if (-not $meResult.ok) {
                throw "移动端会话未保持：$($meResult.status) $($meResult.text)"
            }
            $loginData = $loginResult.text | ConvertFrom-Json
            $userJson = $loginData.data | ConvertTo-Json -Compress
            $null = Eval @"
(async () => {
  const [{auth}, routerModule] = await Promise.all([
    import('/src/stores/auth.js'),
    import('/src/router/index.js')
  ]);
  auth.user = $userJson;
  await routerModule.default.push('/mobile');
  return true;
})()
"@
        }
    }
    WaitPage "location.pathname === '/mobile' && document.body.innerText.includes('上传到电脑')" 20
    Start-Sleep -Milliseconds 600
    Screenshot '09-mobile-page.png'

    $null = Eval @'
(() => {
  document.querySelector('.field .el-select').click();
  return true;
})()
'@
    WaitPage "[...document.querySelectorAll('.el-select-dropdown__item')].some(e => e.innerText.includes('F002'))"
    $null = Eval @'
(() => {
  [...document.querySelectorAll('.el-select-dropdown__item')]
    .find(e => e.innerText.includes('F002')).click();
  return true;
})()
'@
    $doc = Cdp 'DOM.getDocument' @{ depth = 2 }
    $query = Cdp 'DOM.querySelector' @{ nodeId = $doc.root.nodeId; selector = 'input[type=file]' }
    if (-not $query.nodeId) { throw '手机上传页找不到文件输入框' }
    Cdp 'DOM.setFileInputFiles' @{ nodeId = $query.nodeId; files = @($VideoPath) } | Out-Null
    WaitPage "document.body.innerText.includes('Balance_1_second.mp4')"
    Screenshot '10-mobile-selected.png'

    $null = Eval @'
(() => {
  const button = [...document.querySelectorAll('button')]
    .find(b => b.innerText.includes('上传到电脑并识别'));
  if (!button) throw new Error('找不到上传按钮');
  button.click();
  return true;
})()
'@
    WaitPage "document.body.innerText.includes('已上传并识别完成')" 60
    Start-Sleep -Milliseconds 700
    Screenshot '11-mobile-success.png'
    $text = Eval "document.body.innerText"
    if ($text -notmatch '记录 JC\d+' -or $text -notmatch '电脑端') {
        throw '移动端结果页缺少记录号或电脑端提示'
    }
    Write-Host '移动端局域网验收通过：登录、选择天平、上传、后台识别与成功提示。'
} catch {
    try {
        Write-Host ("当前地址：" + (Eval "location.href"))
        Write-Host ("页面文本：" + ((Eval "document.body.innerText") -replace "\s+", ' '))
        $meDebug = Eval @'
fetch('/api/auth/me', {credentials: 'include'})
  .then(async response => ({status: response.status, text: await response.text()}))
'@
        Write-Host ("会话状态：" + $meDebug.status + " " + $meDebug.text)
        Screenshot 'mobile-acceptance-failure.png'
    } catch {
        Write-Host '无法读取失败页面状态。'
    }
    throw
} finally {
    try { $ws.Dispose() } catch { }
}
