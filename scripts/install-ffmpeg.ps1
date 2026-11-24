# 安装 Gyan.dev 提供的 Windows ffmpeg 稳定构建，并校验官方 SHA-256。
# 用法：pwsh -File scripts\install-ffmpeg.ps1
param(
    [string]$InstallRoot = 'D:\develop\ffmpeg',
    [string]$DownloadUrl = 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip',
    [string]$ChecksumUrl = 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip.sha256'
)

$ErrorActionPreference = 'Stop'
$workRoot = Join-Path ([IO.Path]::GetTempPath()) ('ffmpeg-install-' + [guid]::NewGuid().ToString('N'))
$zipPath = Join-Path $workRoot 'ffmpeg.zip'
$extractRoot = Join-Path $workRoot 'extract'

try {
    New-Item -ItemType Directory -Path $workRoot -Force | Out-Null
    Write-Host '正在下载 ffmpeg…'
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $zipPath
    Write-Host '正在校验 SHA-256…'
    $checksumText = (Invoke-WebRequest -Uri $ChecksumUrl).Content
    $expected = (($checksumText | Out-String).Trim() -split '\s+')[0].ToLowerInvariant()
    if ($expected -notmatch '^[0-9a-f]{64}$') {
        throw '下载的 SHA-256 文件格式不正确'
    }
    $actual = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $expected) {
        throw "ffmpeg 下载校验失败：expected=$expected actual=$actual"
    }

    Expand-Archive -LiteralPath $zipPath -DestinationPath $extractRoot -Force
    $ffmpegSource = Get-ChildItem -LiteralPath $extractRoot -Filter 'ffmpeg.exe' -Recurse -File |
        Select-Object -First 1
    $ffprobeSource = Get-ChildItem -LiteralPath $extractRoot -Filter 'ffprobe.exe' -Recurse -File |
        Select-Object -First 1
    if (-not $ffmpegSource) {
        throw '压缩包中找不到 ffmpeg.exe'
    }

    $binTarget = Join-Path $InstallRoot 'bin'
    New-Item -ItemType Directory -Path $binTarget -Force | Out-Null
    Copy-Item -LiteralPath $ffmpegSource.FullName -Destination (Join-Path $binTarget 'ffmpeg.exe') -Force
    if ($ffprobeSource) {
        Copy-Item -LiteralPath $ffprobeSource.FullName -Destination (Join-Path $binTarget 'ffprobe.exe') -Force
    }
    @(
        "source=$DownloadUrl",
        "sha256=$expected",
        "installedAt=$((Get-Date).ToString('yyyy-MM-dd HH:mm:ss'))"
    ) | Set-Content -LiteralPath (Join-Path $InstallRoot 'VERSION.txt') -Encoding UTF8

    $installed = Join-Path $binTarget 'ffmpeg.exe'
    $version = (& $installed -version 2>&1 | Select-Object -First 1)
    Write-Host 'ffmpeg 安装完成。' -ForegroundColor Green
    Write-Host ("路径：{0}" -f $installed)
    Write-Host ("版本：{0}" -f $version)
    Write-Host ("环境变量：`$env:APP_RECOGNITION_FFMPEG = '{0}'" -f $installed)
} finally {
    Remove-Item -LiteralPath $workRoot -Recurse -Force -ErrorAction SilentlyContinue
}
