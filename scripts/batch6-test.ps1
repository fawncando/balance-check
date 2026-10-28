# 批次 6 冒烟测试：复核通过/退回/解锁、按天平与日期段查询、记录表导出与权限
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
    $r = Invoke-WebRequest "$base/api/auth/login" -Method Post -ContentType 'application/json' `
        -Body (@{ username = $user; password = $password } | ConvertTo-Json) `
        -SessionVariable web -SkipHttpErrorCheck
    return $web
}

function Measurement($range, $weight, $seq, $nominal, $value, $unit = 'g') {
    return @{ rangeSeq = $range; weightSeq = $weight; measureSeq = $seq; nominalG = $nominal
              displayValue = $value; displayUnit = $unit }
}

function RecordBody($balanceId, $time, $measurements) {
    return @{ balanceId = $balanceId; calibrationTime = $time; cleanOk = $true; levelOk = $true
              internalCalibrationResult = '是'; remark = '批次6复核测试'; measurements = $measurements } |
        ConvertTo-Json -Depth 5
}

function CreateRecord($web, $balanceId, $time, $measurements) {
    $r = Invoke-WebRequest "$base/api/calibrations" -Method Post -WebSession $web `
        -ContentType 'application/json; charset=utf-8' -Body (RecordBody $balanceId $time $measurements) -SkipHttpErrorCheck
    return $r
}

function Ids($rows) {
    return @($rows | ForEach-Object { $_.id })
}

$single = @(
    Measurement 1 1 1 200 199.9998; Measurement 1 1 2 200 200.0002; Measurement 1 1 3 200 199.9999
    Measurement 1 2 1 50 49.9998;  Measurement 1 2 2 50 50.0001;  Measurement 1 2 3 50 50.0000
    Measurement 1 3 1 10 9.9999;   Measurement 1 3 2 10 10.0001;  Measurement 1 3 3 10 10.0000
)

Write-Host "== 批次 6 冒烟测试 $base ==" -ForegroundColor Cyan

$inspector = Login 'liyi' '123456'
Check '检验人员登录' ($inspector.Cookies.Count -gt 0)

$r = CreateRecord $inspector 1 '2026-10-17T09:12:00' $single
$record = ($r.Content | ConvertFrom-Json).data
$recordId = $record.id
$recordNo = $record.recordNo
Check '创建记录为草稿' ($r.StatusCode -eq 200 -and $record.status -eq 'DRAFT') "status=$($record.status)"

$r = Invoke-WebRequest "$base/api/calibrations/$recordId/submit" -Method Post -WebSession $inspector -SkipHttpErrorCheck
Check '提交后进入待复核' ((($r.Content | ConvertFrom-Json).data).status -eq 'SUBMITTED') "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/calibrations/$recordId/review" -Method Post -WebSession $inspector `
    -ContentType 'application/json; charset=utf-8' -Body (@{ result = 'APPROVED'; comment = '自审' } | ConvertTo-Json) -SkipHttpErrorCheck
Check '检验人员不能复核记录' ($r.StatusCode -eq 403) "status=$($r.StatusCode)"

$reviewer = Login 'qiyi' '123456'
Check '复核人员登录' ($reviewer.Cookies.Count -gt 0)

$r = Invoke-WebRequest "$base/api/calibrations?status=SUBMITTED" -WebSession $reviewer -SkipHttpErrorCheck
$pending = ($r.Content | ConvertFrom-Json).data
Check '待复核查询命中刚提交的记录' (@(Ids $pending) -contains $recordId) "count=$(@($pending).Count)"

$r = Invoke-WebRequest "$base/api/calibrations/$recordId/review" -Method Post -WebSession $reviewer `
    -ContentType 'application/json; charset=utf-8' -Body (@{ result = 'REJECTED' } | ConvertTo-Json) -SkipHttpErrorCheck
Check '退回不填意见被拒绝' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/calibrations/$recordId/review" -Method Post -WebSession $reviewer `
    -ContentType 'application/json; charset=utf-8' -Body (@{ result = 'REJECTED'; comment = '第 5 次示值超差，请复核砝码放置' } | ConvertTo-Json) -SkipHttpErrorCheck
$rejected = ($r.Content | ConvertFrom-Json).data
Check '退回后记录回到草稿' ($rejected.status -eq 'DRAFT') "status=$($rejected.status)"
Check '退回结论与意见已保存' ($rejected.reviewResult -eq 'REJECTED' -and $rejected.reviewComment -like '*砝码放置*') `
    "result=$($rejected.reviewResult)"
Check '记录带出复核人姓名' ($rejected.reviewerName -eq '齐一' -and $rejected.reviewedAt) "reviewer=$($rejected.reviewerName)"

$r = Invoke-WebRequest "$base/api/calibrations/$recordId" -Method Put -WebSession $inspector `
    -ContentType 'application/json; charset=utf-8' -Body (RecordBody 1 '2026-10-17T09:12:00' $single) -SkipHttpErrorCheck
Check '退回后检验人员可以修改' ($r.StatusCode -eq 200) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/calibrations/$recordId/submit" -Method Post -WebSession $inspector -SkipHttpErrorCheck
Check '修改后重新提交' ((($r.Content | ConvertFrom-Json).data).status -eq 'SUBMITTED') "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/calibrations/$recordId/review" -Method Post -WebSession $reviewer `
    -ContentType 'application/json; charset=utf-8' -Body (@{ result = 'APPROVED'; comment = '示值稳定，同意通过' } | ConvertTo-Json) -SkipHttpErrorCheck
$approved = ($r.Content | ConvertFrom-Json).data
Check '复核通过后记录锁定' ($approved.status -eq 'REVIEWED' -and $approved.reviewedAt) "status=$($approved.status)"
Check '复核通过覆盖上一条退回意见' ($approved.reviewResult -eq 'APPROVED' -and $approved.reviewComment -like '*同意通过*') `
    "comment=$($approved.reviewComment)"

$r = Invoke-WebRequest "$base/api/calibrations/$recordId" -Method Put -WebSession $inspector `
    -ContentType 'application/json; charset=utf-8' -Body (RecordBody 1 '2026-10-17T09:12:00' $single) -SkipHttpErrorCheck
Check '已复核记录禁止修改' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/calibrations/$recordId/review" -Method Post -WebSession $reviewer `
    -ContentType 'application/json; charset=utf-8' -Body (@{ result = 'APPROVED' } | ConvertTo-Json) -SkipHttpErrorCheck
Check '非待复核状态不能再复核' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$other = Login 'lier' '123456'
$r = Invoke-WebRequest "$base/api/calibrations" -WebSession $other -SkipHttpErrorCheck
Check '检验人员查询不到他人记录' (-not (@(Ids (($r.Content | ConvertFrom-Json).data)) -contains $recordId))
$r = Invoke-WebRequest "$base/api/calibrations/$recordId" -WebSession $other -SkipHttpErrorCheck
Check '检验人员查看他人记录返回 403' ($r.StatusCode -eq 403) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/calibrations?balanceId=1" -WebSession $reviewer -SkipHttpErrorCheck
Check '按天平查询命中记录' (@(Ids (($r.Content | ConvertFrom-Json).data)) -contains $recordId)
$r = Invoke-WebRequest "$base/api/calibrations?balanceId=2" -WebSession $reviewer -SkipHttpErrorCheck
Check '按天平查询排除其他天平记录' (-not (@(Ids (($r.Content | ConvertFrom-Json).data)) -contains $recordId))

$r = Invoke-WebRequest "$base/api/calibrations?dateFrom=2026-10-17&dateTo=2026-10-17" -WebSession $reviewer -SkipHttpErrorCheck
Check '按日期段查询命中记录' (@(Ids (($r.Content | ConvertFrom-Json).data)) -contains $recordId)
$r = Invoke-WebRequest "$base/api/calibrations?dateFrom=2026-10-18&dateTo=2026-10-20" -WebSession $reviewer -SkipHttpErrorCheck
Check '日期段外不返回该记录' (-not (@(Ids (($r.Content | ConvertFrom-Json).data)) -contains $recordId))

$r = Invoke-WebRequest "$base/api/calibrations?recordNo=$recordNo" -WebSession $reviewer -SkipHttpErrorCheck
Check '按记录编号关键字查询' (@(Ids (($r.Content | ConvertFrom-Json).data)) -contains $recordId)
$r = Invoke-WebRequest "$base/api/calibrations?recordNo=NOT-EXIST-9527" -WebSession $reviewer -SkipHttpErrorCheck
Check '不存在的记录编号返回空列表' (@(($r.Content | ConvertFrom-Json).data).Count -eq 0)

$r = Invoke-WebRequest "$base/api/calibrations?status=UNKNOWN" -WebSession $reviewer -SkipHttpErrorCheck
Check '非法状态取值被拒绝' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"
$r = Invoke-WebRequest "$base/api/calibrations?dateFrom=2026-10-20&dateTo=2026-10-10" -WebSession $reviewer -SkipHttpErrorCheck
Check '开始日期晚于结束日期被拒绝' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/calibrations/export?id=$recordId" -WebSession $reviewer -SkipHttpErrorCheck
Check '复核人员导出记录表' ($r.StatusCode -eq 200 -and $r.Content -like '*电子天平校验记录表*') "status=$($r.StatusCode)"
Check '导出内容含记录编号与复核人' ($r.Content -like "*$recordNo*" -and $r.Content -like '*齐一*')
Check '导出为可打印 HTML' ($r.Headers['Content-Type'] -like '*text/html*') "type=$($r.Headers['Content-Type'])"

$r = Invoke-WebRequest "$base/api/calibrations/export?id=$recordId" -WebSession $inspector -SkipHttpErrorCheck
Check '检验人员不能导出记录表' ($r.StatusCode -eq 403) "status=$($r.StatusCode)"
$r = Invoke-WebRequest "$base/api/calibrations/export" -WebSession $reviewer -SkipHttpErrorCheck
Check '导出缺少记录编号返回 400' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/calibrations/$recordId/reopen" -Method Post -WebSession $reviewer -SkipHttpErrorCheck
Check '解锁已复核记录回到草稿' ((($r.Content | ConvertFrom-Json).data).status -eq 'DRAFT') "status=$($r.StatusCode)"
$r = Invoke-WebRequest "$base/api/calibrations/$recordId/reopen" -Method Post -WebSession $reviewer -SkipHttpErrorCheck
Check '非已复核记录不能解锁' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/calibrations/$recordId" -Method Put -WebSession $inspector `
    -ContentType 'application/json; charset=utf-8' -Body (RecordBody 1 '2026-10-17T09:12:00' $single) -SkipHttpErrorCheck
Check '解锁后检验人员可以继续修改' ($r.StatusCode -eq 200) "status=$($r.StatusCode)"

Write-Host ""
Write-Host ("结果：{0} 通过，{1} 失败" -f $pass, $fail) -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
exit $(if ($fail -eq 0) { 0 } else { 1 })
