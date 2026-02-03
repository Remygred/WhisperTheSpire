$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$libDir = Join-Path $projectRoot 'lib'
$gameDir = 'E:\SteamLibrary\steamapps\common\SlayTheSpire'
$workshopDir = 'E:\SteamLibrary\steamapps\workshop\content\646570'

New-Item -ItemType Directory -Force -Path $libDir | Out-Null

$rootJarNames = @('desktop-1.0.jar', 'BaseMod.jar', 'ModTheSpire.jar')
foreach ($jarName in $rootJarNames) {
    $rootJarPath = Join-Path $projectRoot $jarName
    if (Test-Path $rootJarPath) {
        Write-Warning "检测到根目录存在同名 jar，已删除: $rootJarPath"
        Remove-Item -Force $rootJarPath
    }
}

function Find-LatestJar {
    param(
        [Parameter(Mandatory = $true)][string]$Pattern
    )

    $candidates = @()

    $candidates += Get-ChildItem -Path $gameDir -Filter $Pattern -File -ErrorAction SilentlyContinue
    $candidates += Get-ChildItem -Path (Join-Path $gameDir 'mods') -Filter $Pattern -File -ErrorAction SilentlyContinue

    if ($candidates.Count -eq 0 -and (Test-Path $workshopDir)) {
        $candidates += Get-ChildItem -Path $workshopDir -Recurse -Filter $Pattern -File -ErrorAction SilentlyContinue
    }

    if ($candidates.Count -eq 0) {
        return $null
    }

    return $candidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

$desktopSrc = Join-Path $gameDir 'desktop-1.0.jar'
if (!(Test-Path $desktopSrc)) {
    Write-Error "desktop-1.0.jar not found at: $desktopSrc"
    exit 1
}
Copy-Item -Force $desktopSrc (Join-Path $libDir 'desktop-1.0.jar')

$baseModJar = Find-LatestJar -Pattern 'BaseMod*.jar'
if (-not $baseModJar) {
    Write-Error 'BaseMod jar not found. Please subscribe/install BaseMod in Steam Workshop.'
    exit 1
}
Copy-Item -Force $baseModJar.FullName (Join-Path $libDir 'BaseMod.jar')

$mtsJar = Find-LatestJar -Pattern 'ModTheSpire*.jar'
if (-not $mtsJar) {
    Write-Error 'ModTheSpire jar not found. Please install ModTheSpire.'
    exit 1
}
Copy-Item -Force $mtsJar.FullName (Join-Path $libDir 'ModTheSpire.jar')

Write-Host "Dependencies copied to $libDir"
