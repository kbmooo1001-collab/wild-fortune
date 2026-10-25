# Use the project-local JDK and Gradle cache without changing system settings.
$ErrorActionPreference = 'Stop'
$projectJdk = Join-Path $PSScriptRoot '.tools\jdk\jdk-25.0.4.1+1'
if (-not (Test-Path -LiteralPath (Join-Path $projectJdk 'bin\java.exe'))) {
    throw 'Project JDK 25 is missing. See README.md.'
}
$previousJavaHome = $env:JAVA_HOME
$previousGradleHome = $env:GRADLE_USER_HOME
Push-Location $PSScriptRoot
try {
    $env:JAVA_HOME = $projectJdk
    $env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.tools\gradle-cache'
    $gradleArguments = if ($args.Count -gt 0) { $args } else { @('build') }
    & (Join-Path $PSScriptRoot 'gradlew.bat') @gradleArguments
    $gradleExitCode = $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:GRADLE_USER_HOME = $previousGradleHome
    Pop-Location
}
exit $gradleExitCode
