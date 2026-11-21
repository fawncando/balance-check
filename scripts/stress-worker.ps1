param(
    [ValidateSet('recognition', 'record')]
    [string]$Mode,
    [hashtable]$Case,
    [int]$Index = 0,
    [string]$BaseUrl,
    [string]$VideoDir,
    [string]$BalanceCode
)

$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')

if ($Mode -eq 'recognition') {
    $session = $null
    $login = @{ username = $Case.username; password = '123456' } |
        ConvertTo-Json -Compress
    Invoke-WebRequest "$base/api/auth/login" -Method Post `
        -ContentType 'application/json' -Body $login -SessionVariable session | Out-Null
    $balances = ((Invoke-WebRequest "$base/api/balances" -WebSession $session).Content |
        ConvertFrom-Json).data
    $balance = $balances | Where-Object { $_.code -eq $Case.balance } | Select-Object -First 1
    $file = Get-Item (Join-Path $VideoDir $Case.video)
    $r = Invoke-WebRequest "$base/api/videos" -Method Post -WebSession $session `
        -Form @{ file = $file; balanceId = [string]$balance.id }
    $videoId = ($r.Content | ConvertFrom-Json).data.id
    Invoke-WebRequest "$base/api/videos/$videoId/analyze" -Method Post -WebSession $session |
        Out-Null
    $deadline = (Get-Date).AddMinutes(3)
    $video = $null
    while ((Get-Date) -lt $deadline) {
        $video = ((Invoke-WebRequest "$base/api/videos/$videoId" -WebSession $session).Content |
            ConvertFrom-Json).data
        if ($video.status -in @('ANALYZED', 'FAILED')) { break }
        Start-Sleep -Milliseconds 700
    }
    [pscustomobject]@{
        user = $Case.username
        balance = $Case.balance
        video = $Case.video
        videoId = $videoId
        status = $video.status
        calibrationId = $video.calibrationId
        confidence = $video.analysisConfidence
        identityVerified = $video.identityVerified
        message = $video.message
    }
    exit 0
}

$session = $null
$username = if ($Index % 2 -eq 0) { 'liyi' } else { 'lier' }
$login = @{ username = $username; password = '123456' } | ConvertTo-Json -Compress
Invoke-WebRequest "$base/api/auth/login" -Method Post -ContentType 'application/json' `
    -Body $login -SessionVariable session | Out-Null
$balances = ((Invoke-WebRequest "$base/api/balances" -WebSession $session).Content |
    ConvertFrom-Json).data
$balance = $balances | Where-Object { $_.code -eq $BalanceCode } | Select-Object -First 1
$measurements = @()
foreach ($range in $balance.ranges) {
    for ($weight = 0; $weight -lt $range.weights.Count; $weight++) {
        for ($measure = 1; $measure -le 3; $measure++) {
            $measurements += @{
                rangeSeq = $range.seqNo
                weightSeq = $weight + 1
                measureSeq = $measure
                nominalG = [double]$range.weights[$weight]
                displayValue = [double]$range.weights[$weight]
                displayUnit = 'g'
                manuallyCorrected = $true
            }
        }
    }
}
$body = @{
    balanceId = $balance.id
    calibrationTime = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ss')
    cleanOk = $true
    levelOk = $true
    internalCalibrationResult = '是'
    remark = "并发压力测试 #$Index"
    measurements = $measurements
} | ConvertTo-Json -Depth 8
$record = ((Invoke-WebRequest "$base/api/calibrations" -Method Post `
    -WebSession $session -ContentType 'application/json; charset=utf-8' -Body $body).Content |
    ConvertFrom-Json).data
Invoke-WebRequest "$base/api/calibrations/$($record.id)/submit" -Method Post `
    -WebSession $session | Out-Null

$reviewer = $null
$reviewLogin = @{ username = 'qiyi'; password = '123456' } | ConvertTo-Json -Compress
Invoke-WebRequest "$base/api/auth/login" -Method Post -ContentType 'application/json' `
    -Body $reviewLogin -SessionVariable reviewer | Out-Null
$reviewBody = @{ result = 'APPROVED'; comment = '并发复核通过' } | ConvertTo-Json -Compress
Invoke-WebRequest "$base/api/calibrations/$($record.id)/review" -Method Post `
    -WebSession $reviewer -ContentType 'application/json' -Body $reviewBody | Out-Null
[pscustomobject]@{
    index = $Index
    recordId = $record.id
    recordNo = $record.recordNo
    status = 'REVIEWED'
}
