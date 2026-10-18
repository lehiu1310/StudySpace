param([string[]]$Tasks = @(':app:assembleDebug', ':app:testDebugUnitTest'))
$ErrorActionPreference = 'Stop'
if (Get-Process qemu-system-x86_64,emulator -ErrorAction SilentlyContinue) {
    throw 'Medium Phone is running. Close the emulator before building StudySpace.'
}
$env:JAVA_HOME = 'D:\adr studio\jbr'
$env:GRADLE_USER_HOME = 'D:\DevData\Gradle'
$env:ANDROID_HOME = 'D:\DevTools\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_AVD_HOME = 'D:\DevData\Android\avd'
$tempPath = 'D:\DevData\Temp\StudySpace'
New-Item -ItemType Directory -Path $tempPath -Force | Out-Null
$env:TEMP = $tempPath
$env:TMP = $tempPath
& (Join-Path (Split-Path $PSScriptRoot -Parent) 'gradlew.bat') @Tasks --console=plain --no-daemon --max-workers=1 --no-build-cache --no-configuration-cache
exit $LASTEXITCODE
