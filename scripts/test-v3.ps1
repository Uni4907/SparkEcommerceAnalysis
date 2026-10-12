param(
    [string]$JavaHome = 'C:\Program Files\Java\jdk-17',
    [string]$HadoopHome = 'D:\Environment\hadoop-3.3.6'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = $JavaHome
$env:HADOOP_HOME = $HadoopHome
$env:Path = (Join-Path $JavaHome 'bin') + ';' + (Join-Path $HadoopHome 'bin') + ';' + $env:Path
Push-Location (Join-Path $projectRoot 'spark')
try { & mvn.cmd test; if ($LASTEXITCODE -ne 0) { throw "Tests failed: $LASTEXITCODE" } } finally { Pop-Location }
