$ErrorActionPreference = 'Stop'
$MavenArgs = @($args)
$scriptDir = $PSScriptRoot
$properties = Get-Content -Raw (Join-Path $scriptDir '.mvn\wrapper\maven-wrapper.properties') |
    ConvertFrom-StringData
$distributionUrl = $properties.distributionUrl
if (-not $distributionUrl) {
    throw "Cannot read distributionUrl from .mvn\wrapper\maven-wrapper.properties"
}

$distributionName = $distributionUrl -replace '^.*/', ''
$distributionMain = $distributionName -replace '\.[^.]*$', '' -replace '-bin$', ''
$mavenUserHome = if ($env:MAVEN_USER_HOME) { $env:MAVEN_USER_HOME } else { Join-Path $HOME '.m2' }
$hash = [System.Security.Cryptography.SHA256]::Create()
try {
    $hashBytes = $hash.ComputeHash([Text.Encoding]::UTF8.GetBytes($distributionUrl))
} finally {
    $hash.Dispose()
}
$distributionHash = ($hashBytes | ForEach-Object { $_.ToString('x2') }) -join ''
$mavenParent = Join-Path $mavenUserHome "wrapper\dists\$distributionMain"
$mavenHome = Join-Path $mavenParent $distributionHash
$mavenCmd = Join-Path $mavenHome 'bin\mvn.cmd'

if (-not (Test-Path -LiteralPath $mavenCmd)) {
    $tempZip = Join-Path ([IO.Path]::GetTempPath()) $distributionName
    $tempDir = Join-Path ([IO.Path]::GetTempPath()) ("maven-wrapper-" + [guid]::NewGuid().ToString('N'))
    try {
        Write-Host "Downloading Maven from $distributionUrl"
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $distributionUrl -OutFile $tempZip
        Expand-Archive -LiteralPath $tempZip -DestinationPath $tempDir -Force
        $extracted = Join-Path $tempDir $distributionMain
        if (-not (Test-Path -LiteralPath $extracted)) {
            $extracted = Get-ChildItem -LiteralPath $tempDir -Directory |
                Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\mvn.cmd') } |
                Select-Object -First 1 -ExpandProperty FullName
        }
        if (-not $extracted) {
            throw "Cannot find Maven distribution in the downloaded archive"
        }
        New-Item -ItemType Directory -Path $mavenParent -Force | Out-Null
        Move-Item -LiteralPath $extracted -Destination $mavenHome -Force
    } finally {
        Remove-Item -LiteralPath $tempZip -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $tempDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if (-not (Test-Path -LiteralPath $mavenCmd)) {
    throw "Maven wrapper distribution is unavailable and could not be installed"
}

& $mavenCmd @MavenArgs
exit $LASTEXITCODE
