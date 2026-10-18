$ErrorActionPreference = 'Stop'
$env:ANDROID_HOME = 'D:\DevTools\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_AVD_HOME = 'D:\DevData\Android\avd'
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$emulator = Join-Path $env:ANDROID_HOME 'emulator\emulator.exe'
$apk = Join-Path (Split-Path $PSScriptRoot -Parent) 'app\build\outputs\apk\debug\app-debug.apk'
$serial = 'emulator-5554'
$package = 'com.aistudio.studyspace.vuxkwa'
$emulatorProcess = $null
$existingQemuIds = @((Get-Process -Name qemu-system-x86_64 -ErrorAction SilentlyContinue).Id)

if (!(Test-Path $apk)) {
    throw "Khong tim thay APK: $apk. Hay build app truoc."
}

$acceleration = (& $emulator -accel-check 2>&1 | Out-String)
if ($acceleration -notmatch 'is installed and usable') {
    throw "Android Emulator chua co hardware acceleration kha dung. Hay khoi dong lai Windows sau khi bat WHPX, roi chay lai. Chi tiet: $acceleration"
}

$device = & $adb devices | Select-String "^$serial\s+"
if (!$device) {
    $emulatorProcess = Start-Process -FilePath $emulator -ArgumentList @(
        '-avd', 'Medium_Phone', '-no-snapshot', '-no-boot-anim',
        '-no-audio', '-gpu', 'swiftshader_indirect'
    ) -PassThru
}

$deadline = (Get-Date).AddMinutes(3)
$bootedOk = $false
while ((Get-Date) -lt $deadline) {
    $device = & $adb devices | Where-Object { $_ -match "^$serial\s+" } | Select-Object -First 1
    if ($device -match '\sunauthorized\s*$') {
        Write-Host 'Hay bam Allow cho khoa xac thuc ADB tren Medium Phone.'
        Start-Sleep -Seconds 5
        continue
    }
    if ($device -match '\sdevice\s*$') {
        $booted = & $adb -s $serial shell getprop sys.boot_completed 2>$null
        if ($booted -eq '1') { $bootedOk = $true; break }
    }
    Start-Sleep -Seconds 5
}

if (!$bootedOk) {
    if ($emulatorProcess -and !$emulatorProcess.HasExited) {
        Stop-Process -Id $emulatorProcess.Id -Force -ErrorAction SilentlyContinue
    }
    Get-Process -Name qemu-system-x86_64 -ErrorAction SilentlyContinue |
        Where-Object { $_.Id -notin $existingQemuIds } |
        Stop-Process -Force -ErrorAction SilentlyContinue
    throw 'Medium Phone chua boot xong sau 3 phut. Da dung emulator duoc tao boi script.'
}

& $adb -s $serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'Cai APK that bai.' }
& $adb -s $serial shell monkey -p $package 1
if ($LASTEXITCODE -ne 0) { throw 'Khong mo duoc StudySpace.' }
