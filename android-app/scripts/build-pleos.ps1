param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$appRoot = Split-Path -Parent $PSScriptRoot
$oldJava = $env:JAVA_HOME
$oldCache = $env:GRADLE_USER_HOME
$mappedDrive = $null
$buildRoot = $appRoot
$buildOutputs = Join-Path $env:TEMP 'Nostalife-Contest-build-v5'
try {
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr' }
    if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) { throw 'Install Android Studio with its bundled JDK.' }
    if ($appRoot -match '[^\x00-\x7F]') {
        foreach ($letter in @('N','P','R','V','Z')) {
            if (-not (Test-Path -LiteralPath ($letter + ':\'))) {
                & subst.exe ($letter + ':') $appRoot
                if ($LASTEXITCODE -eq 0) { $mappedDrive = $letter; $buildRoot = $letter + ':\'; break }
            }
        }
        if (-not $mappedDrive) { throw 'A free drive letter is needed for the Unicode-safe build path.' }
    }
    $env:GRADLE_USER_HOME = Join-Path $buildRoot '.gradle-home'
    $gradleExe = Join-Path $buildRoot 'tools/gradle-9.4.1/bin/gradle.bat'
    if (-not (Test-Path -LiteralPath $gradleExe)) { $gradleExe = Join-Path $buildRoot 'gradlew.bat' }
    Push-Location -LiteralPath $buildRoot
    try {
        $buildArgs = @(':pleos-app:assembleDebug', ':vehicle-probe:assembleDebug', ':pleos-app:testDebugUnitTest',
            ':vehicle-probe:testDebugUnitTest', ':app:testDebugUnitTest', ':pleos-app:processReleaseMainManifest',
            '--console=plain', '--no-daemon', '--max-workers=1', '-Pkotlin.compiler.execution.strategy=in-process',
            '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8')
        $buildArgs += @('--init-script', (Join-Path $buildRoot 'scripts/build-output.gradle'), "-Dnostalife.buildRoot=$buildOutputs")
        if ($Offline) { $buildArgs += '--offline' }
        & $gradleExe @buildArgs
        if ($LASTEXITCODE -ne 0) { throw "Build failed: $LASTEXITCODE" }
    } finally { Pop-Location }
    $dist = Join-Path $appRoot 'dist'
    New-Item -ItemType Directory -Path $dist -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $buildOutputs 'pleos-app/outputs/apk/debug/pleos-app-debug.apk') -Destination (Join-Path $dist 'Nostalife-Pleos-5.3.0-debug.apk') -Force
    Copy-Item -LiteralPath (Join-Path $buildOutputs 'vehicle-probe/outputs/apk/debug/vehicle-probe-debug.apk') -Destination (Join-Path $dist 'Nostalife-Vehicle-Probe-debug.apk') -Force
    Get-FileHash -LiteralPath (Join-Path $dist 'Nostalife-Pleos-5.3.0-debug.apk') -Algorithm SHA256
} finally {
    $env:JAVA_HOME = $oldJava
    $env:GRADLE_USER_HOME = $oldCache
    if ($mappedDrive) { & subst.exe ($mappedDrive + ':') /d }
}
