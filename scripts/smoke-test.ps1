# 批次 3 冒烟测试：登录、权限、天平清单、视频上传与删除
# 用法：pwsh -File scripts/smoke-test.ps1 [-BaseUrl http://localhost:8081] [-VideoPath D:\path\video.mp4]
param(
    [string]$BaseUrl = 'http://localhost:8081',
    [string]$VideoPath = ''
)

$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')
$script:pass = 0
$script:fail = 0
$tempFile = $null

function Check([string]$name, [bool]$condition, [string]$detail = '') {
    if ($condition) {
        $script:pass++
        Write-Host ("PASS  " + $name) -ForegroundColor Green
    } else {
        $script:fail++
        Write-Host ("FAIL  " + $name + "  " + $detail) -ForegroundColor Red
    }
}

function Body($response) {
    return $response.Content | ConvertFrom-Json
}

try {
    Write-Host "== 批次 3 冒烟测试 $base ==" -ForegroundColor Cyan

    $r = Invoke-WebRequest "$base/api/system/health" -SkipHttpErrorCheck
    $b = Body $r
    Check '健康检查返回 UP' ($r.StatusCode -eq 200 -and $b.data.status -eq 'UP') "status=$($r.StatusCode)"

    $r = Invoke-WebRequest "$base/api/videos" -SkipHttpErrorCheck
    Check '未登录访问视频接口返回 401' ($r.StatusCode -eq 401) "status=$($r.StatusCode)"

    $r = Invoke-WebRequest "$base/api/auth/login" -Method Post -ContentType 'application/json' `
        -Body '{"username":"liyi","password":"wrong-password"}' -SkipHttpErrorCheck
    $b = Body $r
    Check '错误密码返回 401 且提示明确' ($r.StatusCode -eq 401 -and $b.message -eq '账号或密码错误') `
        "status=$($r.StatusCode) message=$($b.message)"

    $r = Invoke-WebRequest "$base/api/auth/login" -Method Post -ContentType 'application/json' `
        -Body '{"username":"liyi","password":"123456"}' -SessionVariable web -SkipHttpErrorCheck
    $b = Body $r
    Check '检验人员登录成功' ($r.StatusCode -eq 200 -and $b.data.role -eq 'INSPECTOR') "role=$($b.data.role)"

    $r = Invoke-WebRequest "$base/api/auth/me" -WebSession $web -SkipHttpErrorCheck
    $b = Body $r
    Check '会话保持，当前用户为李一' ($b.data.username -eq 'liyi' -and $b.data.cardNo -eq 'RY-001') "user=$($b.data.username)"

    $r = Invoke-WebRequest "$base/api/balances" -WebSession $web -SkipHttpErrorCheck
    $b = Body $r
    $f001 = $b.data | Where-Object { $_.code -eq 'F001' }
    $f002 = $b.data | Where-Object { $_.code -eq 'F002' }
    Check '天平清单返回 F001/F002' ($null -ne $f001 -and $null -ne $f002)
    $weights = @($f001.ranges[0].weights | ForEach-Object { [double]$_ })
    Check 'F001 砝码组为 200/50/10 g' ($weights.Count -eq 3 -and $weights[0] -eq 200 -and $weights[1] -eq 50 -and $weights[2] -eq 10) `
        "weights=$($weights -join ',')"
    Check 'F002 为双量程 120 g 与 40 g' ($f002.ranges.Count -eq 2 -and $f002.ranges[1].maxCapacityG -eq 40)

    if ($VideoPath -and (Test-Path $VideoPath)) {
        $file = Get-Item $VideoPath
    } else {
        $tempFile = Join-Path ([System.IO.Path]::GetTempPath()) ("smoke-" + [guid]::NewGuid().ToString('N') + ".mp4")
        [System.IO.File]::WriteAllBytes($tempFile, (New-Object byte[] 2048))
        $file = Get-Item $tempFile
    }

    $r = Invoke-WebRequest "$base/api/videos" -Method Post -WebSession $web -SkipHttpErrorCheck `
        -Form @{ file = $file; balanceId = '9999' }
    Check '不存在的天平拒绝上传' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

    $r = Invoke-WebRequest "$base/api/videos" -Method Post -WebSession $web -SkipHttpErrorCheck `
        -Form @{ file = $file; balanceId = '1' }
    $b = Body $r
    $videoId = $b.data.id
    Check '上传视频成功并返回已上传状态' ($r.StatusCode -eq 200 -and $b.data.status -eq 'UPLOADED') `
        "status=$($r.StatusCode) id=$videoId"
    Check '上传后回传天平编号 F001' ($b.data.balanceCode -eq 'F001') "balance=$($b.data.balanceCode)"

    $r = Invoke-WebRequest "$base/api/videos" -WebSession $web -SkipHttpErrorCheck
    $b = Body $r
    Check '视频列表包含刚上传的记录' (@($b.data | Where-Object { $_.id -eq $videoId }).Count -eq 1)

    $r = Invoke-WebRequest "$base/api/videos/$videoId" -Method Delete -WebSession $web -SkipHttpErrorCheck
    Check '删除自己上传的视频成功' ($r.StatusCode -eq 200) "status=$($r.StatusCode)"

    $r = Invoke-WebRequest "$base/api/videos" -WebSession $web -SkipHttpErrorCheck
    $b = Body $r
    Check '删除后列表中不再出现该记录' (@($b.data | Where-Object { $_.id -eq $videoId }).Count -eq 0)

    $r = Invoke-WebRequest "$base/api/auth/logout" -Method Post -WebSession $web -SkipHttpErrorCheck
    Check '退出登录成功' ($r.StatusCode -eq 200) "status=$($r.StatusCode)"

    $r = Invoke-WebRequest "$base/api/auth/me" -WebSession $web -SkipHttpErrorCheck
    Check '退出后会话失效（401）' ($r.StatusCode -eq 401) "status=$($r.StatusCode)"

} finally {
    if ($tempFile -and (Test-Path $tempFile)) {
        Remove-Item -LiteralPath $tempFile -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ""
Write-Host ("结果：{0} 通过，{1} 失败" -f $script:pass, $script:fail) -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
exit $(if ($script:fail -eq 0) { 0 } else { 1 })
