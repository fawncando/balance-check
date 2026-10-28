# 批次 7 冒烟测试：天平清单维护、操作日志查询、记录表模板与权限
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

function Json($response) {
    return $response.Content | ConvertFrom-Json
}

function Range($seq, $capacity, $division, $setId) {
    return @{ seqNo = $seq; maxCapacityG = $capacity; divisionMg = $division; weightSetId = $setId }
}

function BalanceBody($code, $uniqueNo, $department, $status, $ranges, $rangeCount = -1, $unit = 'g') {
    if ($rangeCount -lt 0) { $rangeCount = @($ranges).Count }
    return @{ code = $code; uniqueNo = $uniqueNo; department = $department; rangeCount = $rangeCount
              internalCalibrationMode = '手动'; defaultUnit = $unit; status = $status; ranges = @($ranges) } |
        ConvertTo-Json -Depth 6
}

$stamp = (Get-Date -Format 'HHmmss') + (Get-Random -Minimum 100 -Maximum 999)
$code = "T$stamp"
$uniqueNo = "TEST-$stamp"
$tplName = "批次7测试模板-$stamp"
$note = '中文说明"含引号"与反斜杠\与制表符'

Write-Host "== 批次 7 冒烟测试 $base ==" -ForegroundColor Cyan

$admin = Login 'admin' '123456'
Check '管理员登录' ($admin.Cookies.Count -gt 0)

$r = Invoke-WebRequest "$base/api/weight-sets" -WebSession $admin -SkipHttpErrorCheck
$sets = @((Json $r).data)
$setA = $sets | Where-Object { $_.setNo -eq 'FM2512920' }
$setB = $sets | Where-Object { $_.setNo -eq 'FM2512921' }
Check '砝码组清单可查询' ($r.StatusCode -eq 200 -and $null -ne $setA -and $null -ne $setB) "status=$($r.StatusCode)"
Check '砝码组带出盒内砝码' ($null -ne $setA -and @($setA.weights).Count -eq 3 -and [double]$setA.weights[0] -eq 200) `
    "weights=$($setA.weights -join ',')"

$inspector = Login 'liyi' '123456'
$reviewer = Login 'qiyi' '123456'

$r = Invoke-WebRequest "$base/api/balances" -Method Post -WebSession $inspector `
    -ContentType 'application/json; charset=utf-8' `
    -Body (BalanceBody $code $uniqueNo '化学室' 'ACTIVE' @(Range 1 220 0.1 $setA.id)) -SkipHttpErrorCheck
Check '检验人员不能维护天平清单' ($r.StatusCode -eq 403) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/balances" -Method Post -WebSession $reviewer `
    -ContentType 'application/json; charset=utf-8' `
    -Body (BalanceBody $code $uniqueNo '化学室' 'ACTIVE' @(Range 1 220 0.1 $setA.id)) -SkipHttpErrorCheck
$created = (Json $r).data
$balanceId = $created.id
Check '复核人员新增天平' ($r.StatusCode -eq 200 -and $created.code -eq $code) "status=$($r.StatusCode)"
Check '新增天平默认启用并带出量程' ($created.status -eq 'ACTIVE' -and @($created.ranges).Count -eq 1) `
    "status=$($created.status) ranges=$(@($created.ranges).Count)"

$r = Invoke-WebRequest "$base/api/balances" -Method Post -WebSession $admin `
    -ContentType 'application/json; charset=utf-8' `
    -Body (BalanceBody $code "TEST2-$stamp" '化学室' 'ACTIVE' @(Range 1 220 0.1 $setA.id)) -SkipHttpErrorCheck
Check '重复天平编号被拒绝' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/balances" -Method Post -WebSession $admin `
    -ContentType 'application/json; charset=utf-8' `
    -Body (BalanceBody '001' "TEST3-$stamp" '化学室' 'ACTIVE' @(Range 1 220 0.1 $setA.id)) -SkipHttpErrorCheck
Check '非法天平编号被拒绝' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/balances" -Method Post -WebSession $admin `
    -ContentType 'application/json; charset=utf-8' `
    -Body (BalanceBody "U$stamp" "TEST4-$stamp" '化学室' 'ACTIVE' @(Range 1 220 0.1 $setA.id) 2) -SkipHttpErrorCheck
Check '量程数量与配置不一致被拒绝' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/balances" -Method Post -WebSession $admin `
    -ContentType 'application/json; charset=utf-8' `
    -Body (BalanceBody "V$stamp" "TEST5-$stamp" '化学室' 'ACTIVE' @(Range 1 10 20000 $setA.id)) -SkipHttpErrorCheck
Check '分度值不小于量程上限被拒绝' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$twoRanges = @((Range 1 220 0.1 $setA.id), (Range 2 40 0.01 $setB.id))
$r = Invoke-WebRequest "$base/api/balances/$balanceId" -Method Put -WebSession $reviewer `
    -ContentType 'application/json; charset=utf-8' `
    -Body (BalanceBody $code $uniqueNo '化学室二' 'ACTIVE' $twoRanges) -SkipHttpErrorCheck
$updated = (Json $r).data
Check '修改天平并替换量程' ($r.StatusCode -eq 200 -and $updated.department -eq '化学室二' -and @($updated.ranges).Count -eq 2) `
    "status=$($r.StatusCode) dept=$($updated.department)"

$r = Invoke-WebRequest "$base/api/balances/$balanceId" -WebSession $reviewer -SkipHttpErrorCheck
$detail = (Json $r).data
Check '天平详情带出量程与砝码组' (@($detail.ranges).Count -eq 2 -and $detail.ranges[0].weightSetNo -eq 'FM2512920') `
    "ranges=$(@($detail.ranges).Count)"

$r = Invoke-WebRequest "$base/api/balances/999999" -Method Put -WebSession $admin `
    -ContentType 'application/json; charset=utf-8' `
    -Body (BalanceBody "W$stamp" "TEST6-$stamp" '化学室' 'ACTIVE' @(Range 1 220 0.1 $setA.id)) -SkipHttpErrorCheck
Check '修改不存在的天平返回 404' ($r.StatusCode -eq 404) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/balances/$balanceId" -Method Put -WebSession $reviewer `
    -ContentType 'application/json; charset=utf-8' `
    -Body (BalanceBody $code $uniqueNo '化学室二' 'INACTIVE' $twoRanges) -SkipHttpErrorCheck
Check '停用天平' ((Json $r).data.status -eq 'INACTIVE') "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/balances" -WebSession $reviewer -SkipHttpErrorCheck
$activeCodes = @((Json $r).data | ForEach-Object { $_.code })
Check '停用后不在天平清单中' (-not ($activeCodes -contains $code))

$r = Invoke-WebRequest "$base/api/balances/$balanceId" -Method Put -WebSession $reviewer `
    -ContentType 'application/json; charset=utf-8' `
    -Body (BalanceBody $code $uniqueNo '化学室二' 'ACTIVE' $twoRanges) -SkipHttpErrorCheck
Check '重新启用天平' ((Json $r).data.status -eq 'ACTIVE') "status=$($r.StatusCode)"
$r = Invoke-WebRequest "$base/api/balances" -WebSession $reviewer -SkipHttpErrorCheck
Check '启用后回到天平清单' (@((Json $r).data | Where-Object { $_.code -eq $code }).Count -eq 1)

$r = Invoke-WebRequest "$base/api/logs?page=1&size=20" -WebSession $admin -SkipHttpErrorCheck
$logs = (Json $r).data
Check '管理员查询操作日志' ($r.StatusCode -eq 200 -and $logs.total -gt 0) "status=$($r.StatusCode)"
Check '日志带出操作人与时间' (@($logs.items | Where-Object { $_.userName -and $_.createdAt }).Count -gt 0)

$r = Invoke-WebRequest "$base/api/logs?action=BALANCE_CREATE&size=50" -WebSession $admin -SkipHttpErrorCheck
$items = @((Json $r).data.items)
Check '按操作类型过滤日志' ($items.Count -gt 0 -and @($items | Where-Object { $_.action -ne 'BALANCE_CREATE' }).Count -eq 0) `
    "count=$($items.Count)"

$r = Invoke-WebRequest "$base/api/users" -WebSession $admin -SkipHttpErrorCheck
$users = @((Json $r).data)
$liyi = $users | Where-Object { $_.username -eq 'liyi' }
Check '管理员可查询用户列表' ($r.StatusCode -eq 200 -and @($users).Count -ge 4) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/logs?userId=$($liyi.id)&size=50" -WebSession $admin -SkipHttpErrorCheck
$items = @((Json $r).data.items)
Check '按操作人过滤日志' ($items.Count -gt 0 -and @($items | Where-Object { $_.userId -ne $liyi.id }).Count -eq 0) `
    "count=$($items.Count)"

$today = (Get-Date).ToString('yyyy-MM-dd')
$r = Invoke-WebRequest "$base/api/logs?dateFrom=$today&dateTo=$today" -WebSession $admin -SkipHttpErrorCheck
Check '按日期段过滤命中当天日志' ((Json $r).data.total -gt 0) "date=$today"

$future = (Get-Date).AddDays(5).ToString('yyyy-MM-dd')
$r = Invoke-WebRequest "$base/api/logs?dateFrom=$future&dateTo=$future" -WebSession $admin -SkipHttpErrorCheck
Check '未来日期段没有日志' ((Json $r).data.total -eq 0) "date=$future"

$r = Invoke-WebRequest "$base/api/logs?page=1&size=1" -WebSession $admin -SkipHttpErrorCheck
$paged = (Json $r).data
Check '日志分页参数生效' ($paged.size -eq 1 -and @($paged.items).Count -le 1) "size=$($paged.size)"

$r = Invoke-WebRequest "$base/api/logs" -WebSession $inspector -SkipHttpErrorCheck
Check '非管理员查询日志返回 403' ($r.StatusCode -eq 403) "status=$($r.StatusCode)"
$r = Invoke-WebRequest "$base/api/users" -WebSession $inspector -SkipHttpErrorCheck
Check '非管理员查询用户列表返回 403' ($r.StatusCode -eq 403) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/templates" -Method Post -WebSession $admin `
    -ContentType 'application/json; charset=utf-8' `
    -Body (@{ name = $tplName; version = 'V1'; note = $note; enabled = $true } | ConvertTo-Json) -SkipHttpErrorCheck
$v1 = (Json $r).data
Check '管理员新增模板版本' ($r.StatusCode -eq 200 -and $v1.enabled -eq $true) "status=$($r.StatusCode)"
Check '模板说明原样回读' ($v1.note -eq $note) "note=$($v1.note)"

$r = Invoke-WebRequest "$base/api/templates" -Method Post -WebSession $admin `
    -ContentType 'application/json; charset=utf-8' `
    -Body (@{ name = $tplName; version = 'V1'; note = '重复'; enabled = $false } | ConvertTo-Json) -SkipHttpErrorCheck
Check '同名同版本被拒绝' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/templates" -Method Post -WebSession $admin `
    -ContentType 'application/json; charset=utf-8' `
    -Body (@{ name = ''; version = 'V1'; note = ''; enabled = $false } | ConvertTo-Json) -SkipHttpErrorCheck
Check '模板名称必填' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/templates" -Method Post -WebSession $admin `
    -ContentType 'application/json; charset=utf-8' `
    -Body (@{ name = $tplName; version = 'V2'; note = '第二版'; enabled = $true } | ConvertTo-Json) -SkipHttpErrorCheck
$v2 = (Json $r).data
Check '新增模板第二版本' ($r.StatusCode -eq 200 -and $v2.version -eq 'V2') "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/templates" -WebSession $admin -SkipHttpErrorCheck
$templates = @((Json $r).data | Where-Object { $_.name -eq $tplName })
$currentV1 = $templates | Where-Object { $_.version -eq 'V1' }
$currentV2 = $templates | Where-Object { $_.version -eq 'V2' }
Check '启用新版本后同名的旧版本自动停用' ($currentV2.enabled -eq $true -and $currentV1.enabled -eq $false) `
    "v1=$($currentV1.enabled) v2=$($currentV2.enabled)"

$r = Invoke-WebRequest "$base/api/templates/$($currentV1.id)/enabled?enabled=true" -Method Put -WebSession $admin -SkipHttpErrorCheck
Check '可以重新启用旧版本' ((Json $r).data.enabled -eq $true) "status=$($r.StatusCode)"
$r = Invoke-WebRequest "$base/api/templates" -WebSession $admin -SkipHttpErrorCheck
$templates = @((Json $r).data | Where-Object { $_.name -eq $tplName })
$v1After = $templates | Where-Object { $_.version -eq 'V1' }
$v2After = $templates | Where-Object { $_.version -eq 'V2' }
Check '旧版本启用后新版本停用' ($v1After.enabled -eq $true -and $v2After.enabled -eq $false) "v1=$($v1After.enabled)"

$r = Invoke-WebRequest "$base/api/templates" -Method Post -WebSession $reviewer `
    -ContentType 'application/json; charset=utf-8' `
    -Body (@{ name = $tplName; version = 'V3'; note = ''; enabled = $false } | ConvertTo-Json) -SkipHttpErrorCheck
Check '非管理员不能维护模板' ($r.StatusCode -eq 403) "status=$($r.StatusCode)"

# 清理：把测试天平停用，避免长期占用备选清单（模板与日志保留，便于查看）
$r = Invoke-WebRequest "$base/api/balances/$balanceId" -Method Put -WebSession $reviewer `
    -ContentType 'application/json; charset=utf-8' `
    -Body (BalanceBody $code $uniqueNo '化学室二' 'INACTIVE' $twoRanges) -SkipHttpErrorCheck
if ((Json $r).data.status -ne 'INACTIVE') {
    Write-Host "WARN  测试天平 $code 未能停用，请手工处理" -ForegroundColor Yellow
}

Write-Host ""
Write-Host ("结果：{0} 通过，{1} 失败" -f $pass, $fail) -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
exit $(if ($fail -eq 0) { 0 } else { 1 })
