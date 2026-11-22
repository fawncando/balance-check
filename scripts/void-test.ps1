# 作废记录接口测试：管理员权限、原因必填、状态锁定和审计日志。
param([string]$BaseUrl = 'http://localhost:8081')

$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')
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

function Measurement($range, $weight, $seq, $nominal, $value) {
    return @{ rangeSeq = $range; weightSeq = $weight; measureSeq = $seq; nominalG = $nominal
              displayValue = $value; displayUnit = 'g' }
}

$measurements = @(
    Measurement 1 1 1 200 200.0000; Measurement 1 1 2 200 200.0001; Measurement 1 1 3 200 199.9999
    Measurement 1 2 1 50 50.0000;   Measurement 1 2 2 50 50.0001;   Measurement 1 2 3 50 49.9999
    Measurement 1 3 1 10 10.0000;   Measurement 1 3 2 10 10.0001;   Measurement 1 3 3 10 9.9999
)

$inspector = Login 'liyi' '123456'
$reviewer = Login 'qiyi' '123456'
$admin = Login 'admin' '123456'

$body = @{
    balanceId = 1
    calibrationTime = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ss')
    cleanOk = $true
    levelOk = $true
    internalCalibrationResult = '是'
    remark = '作废接口测试'
    measurements = $measurements
} | ConvertTo-Json -Depth 6

$r = Invoke-WebRequest "$base/api/calibrations" -Method Post -WebSession $inspector `
    -ContentType 'application/json; charset=utf-8' -Body $body -SkipHttpErrorCheck
$record = ($r.Content | ConvertFrom-Json).data
Check '创建作废测试记录' ($r.StatusCode -eq 200 -and $record.status -eq 'DRAFT') `
    "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/calibrations/$($record.id)/void" -Method Post `
    -WebSession $inspector -ContentType 'application/json' `
    -Body (@{ reason = '检验人员不应能作废' } | ConvertTo-Json) -SkipHttpErrorCheck
Check '检验人员不能作废' ($r.StatusCode -eq 403) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/calibrations/$($record.id)/void" -Method Post `
    -WebSession $reviewer -ContentType 'application/json' `
    -Body (@{ reason = '复核人员不应能作废' } | ConvertTo-Json) -SkipHttpErrorCheck
Check '复核人员不能作废' ($r.StatusCode -eq 403) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/calibrations/$($record.id)/void" -Method Post `
    -WebSession $admin -ContentType 'application/json' `
    -Body (@{ reason = '' } | ConvertTo-Json) -SkipHttpErrorCheck
Check '作废原因必填' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$reason = '甲方复核确认该记录引用错误天平，自动判定结论无效'
$r = Invoke-WebRequest "$base/api/calibrations/$($record.id)/void" -Method Post `
    -WebSession $admin -ContentType 'application/json; charset=utf-8' `
    -Body (@{ reason = $reason } | ConvertTo-Json) -SkipHttpErrorCheck
$voided = ($r.Content | ConvertFrom-Json).data
Check '管理员作废成功' ($r.StatusCode -eq 200 -and $voided.status -eq 'VOIDED') `
    "status=$($r.StatusCode) record=$($voided.status)"
$voidInfoOk = $voided.voidReason -eq $reason -and $voided.voidedByName -eq '管理员' -and $voided.voidedAt
Check '保存作废原因和操作人' $voidInfoOk "by=$($voided.voidedByName)"

$r = Invoke-WebRequest "$base/api/calibrations/$($record.id)" -Method Put `
    -WebSession $admin -ContentType 'application/json; charset=utf-8' -Body $body -SkipHttpErrorCheck
Check '作废后不能修改' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/calibrations/$($record.id)/submit" -Method Post `
    -WebSession $admin -SkipHttpErrorCheck
Check '作废后不能提交' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/calibrations/$($record.id)/review" -Method Post `
    -WebSession $reviewer -ContentType 'application/json' `
    -Body (@{ result = 'APPROVED'; comment = '' } | ConvertTo-Json) -SkipHttpErrorCheck
Check '作废后不能复核' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/calibrations/$($record.id)/reopen" -Method Post `
    -WebSession $reviewer -SkipHttpErrorCheck
Check '作废后不能解锁' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/calibrations?status=VOIDED&recordNo=$($record.recordNo)" `
    -WebSession $admin -SkipHttpErrorCheck
$found = @((($r.Content | ConvertFrom-Json).data))
Check '可按已作废状态查询' ($found.Count -eq 1 -and $found[0].status -eq 'VOIDED') `
    "count=$($found.Count)"

$r = Invoke-WebRequest "$base/api/logs?action=CALIBRATION_VOID&size=50" `
    -WebSession $admin -SkipHttpErrorCheck
$logs = @((($r.Content | ConvertFrom-Json).data.items))
Check '作废动作写入审计日志' (@($logs | Where-Object {
    $_.targetId -eq [string]$record.id -and $_.detail -like "*$reason*"
}).Count -eq 1)

$r = Invoke-WebRequest "$base/api/calibrations/$($record.id)/pdf" `
    -WebSession $reviewer -SkipHttpErrorCheck
$pdfTypeOk = @($r.Headers['Content-Type']) -contains 'application/pdf'
Check '已作废记录仍可归档 PDF' ($r.StatusCode -eq 200 -and $pdfTypeOk) "status=$($r.StatusCode)"

Write-Host ""
Write-Host ("结果：{0} 通过，{1} 失败" -f $pass, $fail) `
    -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
exit $(if ($fail -eq 0) { 0 } else { 1 })
