# 交付前环境检查：JDK、Node、MySQL 客户端、ffmpeg、端口与存储目录
# 用法：pwsh -File scripts\check-environment.ps1 [-UploadDir D:\data\uploads] [-JavaHome <JDK 目录>]
param(
    [int[]]$Ports = @(8081, 5173),
    [string]$UploadDir = 'D:/develop/balance-check-data/uploads',
    [string]$JavaHome = $env:JAVA_HOME,
    [int]$MinFreeGb = 5
)

$ErrorActionPreference = 'Continue'
$script:pass = 0
$script:warn = 0
$script:fail = 0

function Report([string]$level, [string]$name, [string]$detail = '') {
    switch ($level) {
        'PASS' { $script:pass++; Write-Host ("[PASS] {0} {1}" -f $name, $detail) -ForegroundColor Green }
        'WARN' { $script:warn++; Write-Host ("[WARN] {0} {1}" -f $name, $detail) -ForegroundColor Yellow }
        'FAIL' { $script:fail++; Write-Host ("[FAIL] {0} {1}" -f $name, $detail) -ForegroundColor Red }
    }
}

function Test-PortInUse([int]$Port) {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $async = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        $ready = $async.AsyncWaitHandle.WaitOne(400)
        if ($ready) { $client.EndConnect($async) }
        $client.Close()
        return $ready
    } catch {
        return $false
    }
}

Write-Host "== 天平校验视频分析系统 环境检查 ==" -ForegroundColor Cyan -NoNewline
Write-Host ""

# JDK
$java = $null
if ($JavaHome) {
    $candidate = Join-Path $JavaHome 'bin\java.exe'
    if (Test-Path $candidate) { $java = $candidate }
}
if (-not $java) {
    $command = Get-Command java -ErrorAction SilentlyContinue
    if ($command) { $java = $command.Source }
}
if ($java) {
    $versionText = (& $java -version 2>&1 | Out-String)
    if ($versionText -match 'version "(\d+)') {
        $major = [int]$Matches[1]
        if ($major -ge 17) {
            Report 'PASS' 'JDK' ("{0}（{1}）" -f $major, $java)
        } else {
            Report 'FAIL' 'JDK 版本过低' ("{0}，需要 17 及以上" -f $major)
        }
    } else {
        Report 'WARN' '无法解析 JDK 版本' $java
    }
} else {
    Report 'FAIL' '找不到 java' '请安装 JDK 17 及以上，或设置 JAVA_HOME'
}

# Node / npm（仅前端开发与构建需要）
$node = Get-Command node -ErrorAction SilentlyContinue
if ($node) {
    $nodeVersion = (& node -v)
    if ($nodeVersion -match 'v(\d+)' -and [int]$Matches[1] -ge 20) {
        Report 'PASS' 'Node.js' $nodeVersion
    } else {
        Report 'WARN' 'Node.js 版本偏低' ("{0}，建议 20 及以上" -f $nodeVersion)
    }
} else {
    Report 'WARN' '找不到 node' '仅前端开发与构建需要，运行后端不影响'
}
$npm = Get-Command npm -ErrorAction SilentlyContinue
if ($npm) { Report 'PASS' 'npm' ((& npm -v) | Out-String).Trim() }
else { Report 'WARN' '找不到 npm' '仅前端构建需要' }

# MySQL 客户端
$mysql = Get-Command mysql -ErrorAction SilentlyContinue
if ($mysql) { Report 'PASS' 'MySQL 客户端' ((& mysql --version) | Out-String).Trim() }
else { Report 'WARN' '找不到 mysql 客户端' '初始化数据库时需要，可用图形工具代替' }

# ffmpeg（识别原型使用）
$ffmpeg = if ($env:FFMPEG) { Get-Command $env:FFMPEG -ErrorAction SilentlyContinue } else { Get-Command ffmpeg -ErrorAction SilentlyContinue }
if ($ffmpeg) { Report 'PASS' 'ffmpeg' $ffmpeg.Source }
else { Report 'WARN' '找不到 ffmpeg' '批次 4 识别抽帧需要，可用环境变量 FFMPEG 指定' }

# 端口占用
foreach ($port in $Ports) {
    if (Test-PortInUse $port) {
        Report 'WARN' ("端口 {0} 已被占用" -f $port) '如果不是本系统进程，请先释放端口'
    } else {
        Report 'PASS' ("端口 {0} 空闲" -f $port)
    }
}

# 存储目录与磁盘空间
$normalized = $UploadDir.Replace('/', '\')
try {
    $root = [System.IO.Path]::GetPathRoot($normalized)
    if ($root) {
        $drive = New-Object System.IO.DriveInfo $root
        $freeGb = [math]::Round($drive.AvailableFreeSpace / 1GB, 1)
        if ($freeGb -ge $MinFreeGb) {
            Report 'PASS' '存储盘可用空间' ("{0} GB（{1}）" -f $freeGb, $root)
        } else {
            Report 'WARN' '存储盘可用空间不足' ("{0} GB，建议清理或更换 APP_UPLOAD_DIR" -f $freeGb)
        }
    }
} catch {
    Report 'WARN' '无法读取存储盘信息' $UploadDir
}

if (-not (Test-Path $normalized)) {
    try {
        New-Item -ItemType Directory -Path $normalized -Force | Out-Null
        Report 'PASS' '已创建上传目录' $normalized
    } catch {
        Report 'FAIL' '无法创建上传目录' $normalized
    }
}
if (Test-Path $normalized) {
    $probe = Join-Path $normalized ('.probe-' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [System.IO.File]::WriteAllText($probe, 'ok')
        Remove-Item -LiteralPath $probe -Force
        Report 'PASS' '上传目录可写' $normalized
    } catch {
        Report 'FAIL' '上传目录不可写' $normalized
    }
}

Write-Host ""
Write-Host ("结果：{0} 通过，{1} 警告，{2} 失败" -f $script:pass, $script:warn, $script:fail) -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
exit $(if ($script:fail -eq 0) { 0 } else { 1 })
