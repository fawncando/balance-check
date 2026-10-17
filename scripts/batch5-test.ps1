# 批次 5 冒烟测试：记录创建、判定、修改、提交锁定、权限、双量程
param([string]$BaseUrl = 'http://localhost:8081')

$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')
$pass = 0
$fail = 0
$sessionFile = Join-Path $env:TEMP ('bc-' + [guid]::NewGuid().ToString('N') + '.json')

function Check([string]$name, [bool]$ok, [string]$detail = '') {
    if ($ok) { $script:pass++; Write-Host "PASS  $name" -ForegroundColor Green }
    else { $script:fail++; Write-Host "FAIL  $name  $detail" -ForegroundColor Red }
}

function Login([string]$user, [string]$password) {
    $r = Invoke-WebRequest "$base/api/auth/login" -Method Post -ContentType 'application/json' `
        -Body (@{ username = $user; password = $password } | ConvertTo-Json) `
        -SessionVariable web -SkipHttpErrorCheck
    return $web
}

function Measurement($range, $weight, $seq, $nominal, $value, $unit = 'g') {
    return @{ rangeSeq = $range; weightSeq = $weight; measureSeq = $seq; nominalG = $nominal
              displayValue = $value; displayUnit = $unit }
}

$single = @(
    Measurement 1 1 1 200 199.9998; Measurement 1 1 2 200 200.0002; Measurement 1 1 3 200 199.9999
    Measurement 1 2 1 50 49.9998;  Measurement 1 2 2 50 50.0001;  Measurement 1 2 3 50 50.0000
    Measurement 1 3 1 10 9.9999;   Measurement 1 3 2 10 10.0001;  Measurement 1 3 3 10 10.0000
)
$dual = @(
    Measurement 1 1 1 100 100.0002; Measurement 1 1 2 100 99.9999; Measurement 1 1 3 100 100.0000
    Measurement 2 1 1 20 20.00001;  Measurement 2 1 2 20 19.99999; Measurement 2 1 3 20 20.00000
    Measurement 2 2 1 2 2.00001;    Measurement 2 2 2 2 1.99999;   Measurement 2 2 3 2 2.00000
)

try {
    $web = Login 'liyi' '123456'
    Check '检验人员登录' ($web.Cookies.Count -gt 0)

    $body = @{ balanceId = 1; calibrationTime = '2026-10-17T09:12:00'; cleanOk = $true; levelOk = $true
               internalCalibrationResult = '是'; remark = '无'; measurements = $single } | ConvertTo-Json -Depth 5
    $r = Invoke-WebRequest "$base/api/calibrations" -Method Post -WebSession $web -ContentType 'application/json; charset=utf-8' -Body $body -SkipHttpErrorCheck
    $rec = ($r.Content | ConvertFrom-Json).data
    Check '创建合格记录' ($r.StatusCode -eq 200 -and $rec.externalResult -eq '是') "status=$($r.StatusCode)"
    Check '记录包含九次称量' ($rec.measurements.Count -eq 9) "count=$($rec.measurements.Count)"
    Check '200 g 限度正确' ($rec.measurements[0].lowerG -eq 199.9 -and $rec.measurements[0].upperG -eq 200.1) `
        "$($rec.measurements[0].lowerG)~$($rec.measurements[0].upperG)"
    $goodId = $rec.id

    $badMeasurements = $single.Clone()
    $badMeasurements[4] = Measurement 1 2 2 50 50.5000
    $body = @{ balanceId = 1; calibrationTime = '2026-10-17T10:02:00'; cleanOk = $true; levelOk = $true
               internalCalibrationResult = '是'; remark = '演示超差'; measurements = $badMeasurements } | ConvertTo-Json -Depth 5
    $r = Invoke-WebRequest "$base/api/calibrations" -Method Post -WebSession $web -ContentType 'application/json; charset=utf-8' -Body $body -SkipHttpErrorCheck
    $bad = ($r.Content | ConvertFrom-Json).data
    $badRow = $bad.measurements | Where-Object { $_.weightSeq -eq 2 -and $_.measureSeq -eq 2 }
    Check '单次超差判整机不通过' ($bad.externalResult -eq '否' -and $badRow.qualified -eq $false) `
        "result=$($bad.externalResult)"

    $r = Invoke-WebRequest "$base/api/calibrations/$goodId" -WebSession $web -SkipHttpErrorCheck
    Check '按编号查询记录' ($r.StatusCode -eq 200) "status=$($r.StatusCode)"

    $body = @{ balanceId = 1; calibrationTime = '2026-10-17T09:12:00'; cleanOk = $true; levelOk = $true
               internalCalibrationResult = '是'; remark = '补充说明'; measurements = $single } | ConvertTo-Json -Depth 5
    $r = Invoke-WebRequest "$base/api/calibrations/$goodId" -Method Put -WebSession $web -ContentType 'application/json; charset=utf-8' -Body $body -SkipHttpErrorCheck
    Check '修改草稿' ($r.StatusCode -eq 200) "status=$($r.StatusCode)"

    $r = Invoke-WebRequest "$base/api/calibrations/$goodId/submit" -Method Post -WebSession $web -SkipHttpErrorCheck
    $submitted = ($r.Content | ConvertFrom-Json).data
    Check '提交进入待复核' ($submitted.status -eq 'SUBMITTED' -and $submitted.submittedAt) "status=$($submitted.status)"

    $r = Invoke-WebRequest "$base/api/calibrations/$goodId" -Method Put -WebSession $web -ContentType 'application/json; charset=utf-8' -Body $body -SkipHttpErrorCheck
    Check '提交后禁止修改' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

    $reviewer = Login 'qiyi' '123456'
    $body = @{ balanceId = 1; calibrationTime = '2026-10-17T11:00:00'; cleanOk = $true; levelOk = $true
               internalCalibrationResult = '是'; remark = '无'; measurements = $single } | ConvertTo-Json -Depth 5
    $r = Invoke-WebRequest "$base/api/calibrations" -Method Post -WebSession $reviewer -ContentType 'application/json; charset=utf-8' -Body $body -SkipHttpErrorCheck
    Check '复核人员不能创建记录' ($r.StatusCode -eq 403) "status=$($r.StatusCode)"

    $body = @{ balanceId = 1; calibrationTime = '2026-10-17T11:10:00'; measurements = @() } | ConvertTo-Json -Depth 5
    $r = Invoke-WebRequest "$base/api/calibrations" -Method Post -WebSession $web -ContentType 'application/json; charset=utf-8' -Body $body -SkipHttpErrorCheck
    Check '空测量数据被拒绝' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

    $body = @{ balanceId = 2; calibrationTime = '2026-10-17T12:00:00'; cleanOk = $true; levelOk = $true
               internalCalibrationResult = '是'; remark = '双量程'; measurements = $dual } | ConvertTo-Json -Depth 5
    $r = Invoke-WebRequest "$base/api/calibrations" -Method Post -WebSession $web -ContentType 'application/json; charset=utf-8' -Body $body -SkipHttpErrorCheck
    $dualRec = ($r.Content | ConvertFrom-Json).data
    Check '双量程记录创建成功' ($r.StatusCode -eq 200 -and $dualRec.measurements.Count -eq 9 -and $dualRec.externalResult -eq '是') `
        "status=$($r.StatusCode) count=$($dualRec.measurements.Count)"
    Check '双量程判定使用 5 位小数' ($dualRec.measurements[3].lowerG -eq 19.99) "lower=$($dualRec.measurements[3].lowerG)"

} finally {
    Remove-Item -LiteralPath $sessionFile -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host ("结果：{0} 通过，{1} 失败" -f $pass, $fail) -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
exit $(if ($fail -eq 0) { 0 } else { 1 })
