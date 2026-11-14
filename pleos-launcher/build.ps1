$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$apk=Join-Path $projectRoot 'android-app/dist/Nostalife-Pleos-4.2.0-debug.apk'
$payload=Join-Path $PSScriptRoot 'app.apk.gz'
$inputFile=[IO.File]::OpenRead($apk)
$outputFile=[IO.File]::Create($payload)
$gzip=New-Object IO.Compression.GZipStream($outputFile,[IO.Compression.CompressionMode]::Compress)
try{$inputFile.CopyTo($gzip)}finally{$gzip.Dispose();$outputFile.Dispose();$inputFile.Dispose()}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'apk.sha256'),(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant())
$compiler=Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
$exe=Join-Path $projectRoot 'delivery/Nostalife-Pleos.exe'
Push-Location -LiteralPath $PSScriptRoot
try {
    & $compiler /nologo /target:winexe /platform:x64 /optimize+ /langversion:5 /out:Nostalife-Pleos.exe /win32manifest:app.manifest /r:System.Windows.Forms.dll /r:System.Drawing.dll /r:System.Net.Http.dll /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll /resource:app.apk.gz,app.apk.gz /resource:apk.sha256,apk.sha256 /resource:android-license.txt,android-license.txt /resource:pleos-license.txt,pleos-license.txt Launcher.cs
    if($LASTEXITCODE -ne 0){throw 'Launcher compilation failed'}
    Copy-Item -LiteralPath 'Nostalife-Pleos.exe' -Destination $exe -Force
} finally { Pop-Location }
Get-Item -LiteralPath $exe | Select-Object FullName,Length
Get-FileHash -LiteralPath $exe -Algorithm SHA256
