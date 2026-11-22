# 人员卡号维护、权限、唯一性、二维码生成、解绑和审计测试。
param(
    [string]$BaseUrl = 'http://localhost:8081',
    [string]$Python = ''
)

$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')
$repo = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
if (-not $Python) {
    $Python = Join-Path $repo 'analysis\.venv\Scripts\python.exe'
}
$pass = 0
$fail = 0

function Check([string]$name, [bool]$ok, [string]$detail = '') {
    if ($ok) { $script:pass++; Write-Host "PASS  $name" -ForegroundColor Green }
    else { $script:fail++; Write-Host "FAIL  $name  $detail" -ForegroundColor Red }
}

function Login([string]$user, [string]$password) {
    Invoke-WebRequest "$base/api/auth/login" -Method Post -ContentType 'application/json' `
        -Body (@{ username = $user; password = $password } | ConvertTo-Json) `
        -SessionVariable web -SkipHttpErrorCheck | Out-Null
    return $web
}

function CardBody([string]$cardNo) {
    return @{ cardNo = $cardNo } | ConvertTo-Json
}

$admin = Login 'admin' '123456'
$inspector = Login 'liyi' '123456'

$knownUsers = @(((Invoke-WebRequest "$base/api/users" -WebSession $admin).Content |
    ConvertFrom-Json).data)
$knownLiyi = $knownUsers | Where-Object { $_.username -eq 'liyi' } | Select-Object -First 1
Invoke-WebRequest "$base/api/users/$($knownLiyi.id)/card" -Method Put `
    -WebSession $admin -ContentType 'application/json' -Body (CardBody 'RY-001') | Out-Null

$r = Invoke-WebRequest "$base/api/users" -WebSession $admin -SkipHttpErrorCheck
$users = @((($r.Content | ConvertFrom-Json).data))
$liyi = $users | Where-Object { $_.username -eq 'liyi' } | Select-Object -First 1
$lier = $users | Where-Object { $_.username -eq 'lier' } | Select-Object -First 1
Check '管理员查询人员卡号' ($r.StatusCode -eq 200 -and $liyi.cardNo -eq 'RY-001') `
    "card=$($liyi.cardNo)"

$r = Invoke-WebRequest "$base/api/users/$($liyi.id)/card" -Method Put `
    -WebSession $inspector -ContentType 'application/json' -Body (CardBody 'RY-HACK') `
    -SkipHttpErrorCheck
Check '检验人员不能修改人员卡' ($r.StatusCode -eq 403) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/users/$($liyi.id)/card" -Method Put `
    -WebSession $admin -ContentType 'application/json' -Body (CardBody '!') `
    -SkipHttpErrorCheck
Check '非法卡号被拒绝' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/users/$($liyi.id)/card" -Method Put `
    -WebSession $admin -ContentType 'application/json' -Body (CardBody $lier.cardNo) `
    -SkipHttpErrorCheck
Check '重复卡号被拒绝' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/users/$($liyi.id)/card" -Method Put `
    -WebSession $admin -ContentType 'application/json' -Body (CardBody 'RY-TEST01') `
    -SkipHttpErrorCheck
$updated = ($r.Content | ConvertFrom-Json).data
Check '管理员替换人员卡号' ($r.StatusCode -eq 200 -and $updated.cardNo -eq 'RY-TEST01') `
    "card=$($updated.cardNo)"

$qrFile = Join-Path ([IO.Path]::GetTempPath()) ('user-card-' + [guid]::NewGuid().ToString('N') + '.png')
try {
    $r = Invoke-WebRequest "$base/api/users/$($liyi.id)/card/qr" -WebSession $admin `
        -OutFile $qrFile -PassThru -SkipHttpErrorCheck
    $pngBytes = [IO.File]::ReadAllBytes($qrFile)
    $pngHeader = [Text.Encoding]::ASCII.GetString($pngBytes, 1, 3)
    Check '管理员下载人员卡二维码' ($r.StatusCode -eq 200 -and $pngHeader -eq 'PNG') `
        "status=$($r.StatusCode)"

    if (Test-Path $Python) {
        $env:CARD_QR_FILE = $qrFile
        $decoded = & $Python -c "import cv2,os; v,_,_=cv2.QRCodeDetector().detectAndDecode(cv2.imread(os.environ['CARD_QR_FILE'])); print(v)"
        Remove-Item Env:CARD_QR_FILE -ErrorAction SilentlyContinue
        Check '人员卡二维码内容为卡号' ($decoded -eq 'RY-TEST01') "decoded=$decoded"
    } else {
        Write-Host 'WARN  未找到 Python，跳过二维码解码。' -ForegroundColor Yellow
    }
} finally {
    if ([IO.File]::Exists($qrFile)) {
        [IO.File]::Delete($qrFile)
    }
}

$r = Invoke-WebRequest "$base/api/users/$($liyi.id)/card/qr" -WebSession $inspector -SkipHttpErrorCheck
Check '检验人员不能下载人员卡二维码' ($r.StatusCode -eq 403) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/users/$($liyi.id)/card" -Method Delete `
    -WebSession $admin -SkipHttpErrorCheck
$cleared = ($r.Content | ConvertFrom-Json).data
Check '管理员解绑人员卡' ($r.StatusCode -eq 200 -and -not $cleared.cardNo)

$r = Invoke-WebRequest "$base/api/users/$($liyi.id)/card" -Method Put `
    -WebSession $admin -ContentType 'application/json' -Body (CardBody 'RY-001') `
    -SkipHttpErrorCheck
Check '恢复演示人员卡号' ($r.StatusCode -eq 200) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/logs?action=USER_CARD_BIND&size=50" `
    -WebSession $admin -SkipHttpErrorCheck
$bindLogs = @((($r.Content | ConvertFrom-Json).data.items))
Check '绑定卡号写入审计日志' (@($bindLogs | Where-Object {
    $_.targetId -eq [string]$liyi.id
}).Count -ge 2)

$r = Invoke-WebRequest "$base/api/logs?action=USER_CARD_UNBIND&size=50" `
    -WebSession $admin -SkipHttpErrorCheck
$unbindLogs = @((($r.Content | ConvertFrom-Json).data.items))
Check '解绑卡号写入审计日志' (@($unbindLogs | Where-Object {
    $_.targetId -eq [string]$liyi.id
}).Count -ge 1)

Write-Host ""
Write-Host ("结果：{0} 通过，{1} 失败" -f $pass, $fail) `
    -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
exit $(if ($fail -eq 0) { 0 } else { 1 })
