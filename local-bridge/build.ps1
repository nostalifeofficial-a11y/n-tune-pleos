param([string]$Python='python', [string]$OutputDirectory)
$ErrorActionPreference='Stop'
if (-not $OutputDirectory) { $OutputDirectory=Join-Path (Split-Path $PSScriptRoot -Parent) 'delivery' }
Push-Location -LiteralPath $PSScriptRoot
try {
    $buildArgs=@('-m','PyInstaller','--noconfirm','--onefile','--noconsole','--name','Nostalife-LocalBridge',
        '--distpath',$OutputDirectory,'--workpath','../.bridge-build','--specpath','.',
        '--add-data','../web/public;web/public')
    # Some bundled Python runtimes require a certificate distrust list beside ssl.py.
    # Preserve that runtime policy and TLS verification in the standalone executable.
    $certificate=& $Python -c "import pathlib,ssl; p=pathlib.Path(ssl.__file__).with_name('codex-rejected-cas.pem'); print(p if p.exists() else '')"
    if ($certificate) { $buildArgs += @('--add-data',($certificate.Trim()+';.')) }
    $buildArgs += 'bridge.py'
    & $Python @buildArgs
    if ($LASTEXITCODE -ne 0) { throw 'Bridge build failed' }
} finally { Pop-Location }
