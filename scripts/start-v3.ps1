param(
    [int]$Port = 8090,
    [ValidateSet('memory','mysql')][string]$Repository = 'memory',
    [string]$RuntimeDir = '',
    [switch]$EnableHive,
    [ValidatePattern('^\d+[mMgG]$')][string]$MaxHeap = '12g',
    [string]$MavenLocalRepository = '',
    [string]$JavaHome = 'C:\Program Files\Java\jdk-17',
    [string]$HadoopHome = 'D:\Environment\hadoop-3.3.6'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sparkDir = Join-Path $projectRoot 'spark'
if ([string]::IsNullOrWhiteSpace($RuntimeDir)) { $RuntimeDir = Join-Path $sparkDir 'runtime' }
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) { throw "JDK 17 not found: $JavaHome" }
if (-not (Test-Path -LiteralPath (Join-Path $HadoopHome 'bin\winutils.exe'))) { throw "winutils.exe not found: $HadoopHome" }
if (-not (Test-Path -LiteralPath (Join-Path $HadoopHome 'bin\hadoop.dll'))) { throw "hadoop.dll not found: $HadoopHome" }
$missingJdbcVariables = @('PLATFORM_JDBC_URL','PLATFORM_JDBC_USER','PLATFORM_JDBC_PASSWORD') |
    Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_, 'Process')) }
if ($Repository -eq 'mysql' -and $missingJdbcVariables.Count -gt 0) {
    throw 'MySQL mode requires PLATFORM_JDBC_URL, PLATFORM_JDBC_USER and PLATFORM_JDBC_PASSWORD environment variables.'
}

$env:JAVA_HOME = $JavaHome
$env:HADOOP_HOME = $HadoopHome
$env:Path = (Join-Path $JavaHome 'bin') + ';' + (Join-Path $HadoopHome 'bin') + ';' + $env:Path
$env:MAVEN_OPTS = "-Xmx$MaxHeap --add-exports=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens=java.base/sun.nio.cs=ALL-UNNAMED --add-opens=java.base/sun.security.action=ALL-UNNAMED --add-opens=java.base/sun.util.calendar=ALL-UNNAMED"
$mavenArgs = @(
    '-q',
    "-Dplatform.repository=$Repository",
    "-Dplatform.runtime.dir=$RuntimeDir",
    "-Dspark.dashboard.port=$Port"
)
if ($EnableHive) { $mavenArgs += '-Dplatform.hive.enabled=true' }
if (-not [string]::IsNullOrWhiteSpace($MavenLocalRepository)) {
    $mavenArgs += "-Dmaven.repo.local=$MavenLocalRepository"
}
$mavenArgs += 'exec:java@dashboard'

Push-Location $sparkDir
try { & mvn.cmd @mavenArgs } finally { Pop-Location }
