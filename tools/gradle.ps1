# تشغيل Gradle على هذا الجهاز مع JDK 17 وقفل (mutex) يمنع تشغيل عمليتي بناء معًا (ذاكرة الجهاز محدودة).
# مثال:  powershell -File tools/gradle.ps1 :app:assembleDebug
param([Parameter(ValueFromRemainingArguments = $true)] $GradleArgs)

$root = Split-Path -Parent $PSScriptRoot
$toolchain = "C:\Users\hp\yaqeen-toolchain"
$env:JAVA_HOME = "$toolchain\jdk-extract\jdk-17.0.21+9"
$env:ANDROID_HOME = "$toolchain\android-sdk"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

$mutex = New-Object System.Threading.Mutex($false, "Global\YaqeenGradleBuild")
Write-Host "[gradle.ps1] waiting for build lock..."
[void]$mutex.WaitOne()
try {
    Push-Location $root
    & "$root\gradlew.bat" --console=plain @GradleArgs
    $code = $LASTEXITCODE
    Pop-Location
} finally {
    $mutex.ReleaseMutex()
}
exit $code
