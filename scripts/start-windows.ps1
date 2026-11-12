$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root
$Config = Get-Content '.local/windows-config.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$Config.PSObject.Properties | ForEach-Object { [Environment]::SetEnvironmentVariable($_.Name,[string]$_.Value,'Process') }
$Jar = Join-Path $Root 'eldercare.jar'
if (!(Test-Path $Jar)) { $Jar = Join-Path $Root 'target/eldercare-1.0.0.jar' }
& java -jar $Jar
exit $LASTEXITCODE
