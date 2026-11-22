param(
    [switch]$Release
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$mappedDrive = $null

foreach ($letter in 84..90) {
    $candidate = "{0}:" -f [char]$letter
    if (-not (Test-Path -LiteralPath "$candidate\")) {
        $mappedDrive = $candidate
        break
    }
}

if (-not $mappedDrive) {
    throw "No free drive letter is available for the ASCII test path."
}

& subst $mappedDrive $projectRoot
if ($LASTEXITCODE -ne 0) {
    throw "Unable to map $mappedDrive to the project directory."
}

try {
    Push-Location "$mappedDrive\"
    & .\gradlew.bat testDebugUnitTest lintDebug --no-daemon
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    if ($Release) {
        & .\gradlew.bat assembleRelease --no-daemon
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }
}
finally {
    Pop-Location
    & subst $mappedDrive /D
}
