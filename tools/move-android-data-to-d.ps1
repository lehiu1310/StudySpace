$ErrorActionPreference = 'Stop'
$moves = @(
    @{ Source = 'C:\Users\ADMIN\.gradle'; Target = 'D:\DevData\Gradle' },
    @{ Source = 'C:\Users\ADMIN\.android\avd'; Target = 'D:\DevData\Android\avd' },
    @{ Source = 'C:\Users\ADMIN\AppData\Local\Android\Sdk'; Target = 'D:\DevTools\Android\Sdk' }
)
$logRoot = 'D:\baitaplon\tools\migration-logs'
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
if (Get-Process studio64 -ErrorAction SilentlyContinue) { throw 'Close Android Studio before moving its SDK/cache. No files were moved.' }
if (Get-Process qemu-system-x86_64,emulator -ErrorAction SilentlyContinue) { throw 'Close Android emulators before moving their data. No files were moved.' }
$env:JAVA_HOME = 'D:\adr studio\jbr'
& 'D:\baitaplon\gradlew.bat' --stop
if ($LASTEXITCODE -ne 0) { throw 'Could not stop Gradle daemons.' }
& 'C:\Users\ADMIN\AppData\Local\Android\Sdk\platform-tools\adb.exe' kill-server

foreach ($move in $moves) {
    $source = [IO.Path]::GetFullPath($move.Source)
    $target = [IO.Path]::GetFullPath($move.Target)
    if ($source -notin @('C:\Users\ADMIN\.gradle','C:\Users\ADMIN\.android\avd','C:\Users\ADMIN\AppData\Local\Android\Sdk')) { throw 'Source is outside the approved migration scope.' }
    if ($target -notin @('D:\DevData\Gradle','D:\DevData\Android\avd','D:\DevTools\Android\Sdk')) { throw 'Target is outside the approved migration scope.' }
    $item = Get-Item -LiteralPath $source -Force
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        if ([IO.Path]::GetFullPath($item.Target) -eq $target) { Write-Output "Already migrated: $source"; continue }
        throw "Unexpected junction at $source"
    }
    if (Test-Path -LiteralPath $target) { throw "Target already exists; refusing to merge unrelated data: $target" }
    $nestedLinks = Get-ChildItem -LiteralPath $source -Recurse -Force -Attributes ReparsePoint
    if ($nestedLinks) { throw "Nested links require manual review: $source" }
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    $log = Join-Path $logRoot ((Split-Path $target -Leaf) + '-copy.log')
    Write-Output "Copying $source -> $target"
    & robocopy.exe $source $target /E /COPY:DAT /DCOPY:DAT /XJ /R:1 /W:1 /NFL /NDL /NP "/LOG:$log"
    if ($LASTEXITCODE -ge 8) { throw "Copy failed. Original is untouched. See $log" }
    $files = @(Get-ChildItem -LiteralPath $source -File -Recurse -Force)
    $targetFiles = @(Get-ChildItem -LiteralPath $target -File -Recurse -Force)
    if ($files.Count -ne $targetFiles.Count) { throw "File count mismatch. Original is untouched: $source" }
    Write-Output "Verifying SHA-256 for $($files.Count) files in $source"
    $checked = 0
    foreach ($file in $files) {
        $relative = [IO.Path]::GetRelativePath($source, $file.FullName)
        $copy = Join-Path $target $relative
        if (!(Test-Path -LiteralPath $copy) -or $file.Length -ne (Get-Item -LiteralPath $copy -Force).Length) { throw "Copy mismatch: $relative" }
        if ((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $copy -Algorithm SHA256).Hash) { throw "Checksum mismatch: $relative" }
        $checked++
        if ($checked % 5000 -eq 0) { Write-Output "Verified $checked / $($files.Count) files" }
    }
    # Rename on C first; install the compatibility link before removing the verified original.
    $backup = [IO.Path]::GetFullPath($source + '.studyspace-moving')
    if ($backup -ne ($source + '.studyspace-moving') -or (Test-Path -LiteralPath $backup)) { throw 'Invalid or occupied staging path.' }
    Move-Item -LiteralPath $source -Destination $backup
    try {
        New-Item -ItemType Junction -Path $source -Target $target | Out-Null
        $link = Get-Item -LiteralPath $source -Force
        if (!($link.Attributes -band [IO.FileAttributes]::ReparsePoint) -or [IO.Path]::GetFullPath($link.Target) -ne $target) { throw 'Junction verification failed.' }
    } catch {
        if (!(Test-Path -LiteralPath $source)) { Move-Item -LiteralPath $backup -Destination $source }
        throw
    }
    # Both paths are explicitly checked above, and every copied file passed SHA-256 verification.
    if ((Get-Item -LiteralPath $backup -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Staging path unexpectedly became a link.' }
    Remove-Item -LiteralPath $backup -Recurse -Force
    Write-Output "Migrated and verified: $source -> $target"
}
[Environment]::SetEnvironmentVariable('GRADLE_USER_HOME','D:\DevData\Gradle','User')
[Environment]::SetEnvironmentVariable('ANDROID_HOME','D:\DevTools\Android\Sdk','User')
[Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT','D:\DevTools\Android\Sdk','User')
[Environment]::SetEnvironmentVariable('ANDROID_AVD_HOME','D:\DevData\Android\avd','User')
Get-PSDrive C,D | Select-Object Name,@{Name='FreeGB';Expression={[math]::Round($_.Free/1GB,2)}}
