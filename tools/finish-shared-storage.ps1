$ErrorActionPreference = 'Stop'
$pairs = @(
    @{Source='C:\Users\ADMIN\Downloads\flutter_windows_3.35.2-stable\flutter'; Target='D:\DevTools\flutter-3.35.2'},
    @{Source='C:\Users\ADMIN\AppData\Local\Pub\Cache'; Target='D:\DevTools\PubCache'}
)
foreach ($pair in $pairs) {
    $source = $pair.Source
    $target = $pair.Target
    if ([IO.Path]::GetFullPath($source) -cne $source -or [IO.Path]::GetFullPath($target) -cne $target) { throw 'Unexpected path' }
    $original = Get-Item -LiteralPath $source -Force
    if ($original.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        if ($original.Target -eq $target) { continue }
        throw "Unexpected link: $source"
    }
    foreach ($root in @($source,$target)) {
        if (Get-ChildItem -LiteralPath $root -Recurse -Force -Attributes ReparsePoint) { throw "Nested links require review: $root" }
    }
    $files = @(Get-ChildItem -LiteralPath $source -Recurse -Force -File)
    if ($files.Count -ne @(Get-ChildItem -LiteralPath $target -Recurse -Force -File).Count) { throw "File count mismatch: $source" }
    $count = 0
    foreach ($file in $files) {
        $copy = Join-Path $target ([IO.Path]::GetRelativePath($source,$file.FullName))
        if ($file.Length -ne (Get-Item -LiteralPath $copy -Force).Length -or (Get-FileHash -LiteralPath $file.FullName).Hash -ne (Get-FileHash -LiteralPath $copy).Hash) { throw "Verification failed: $($file.FullName)" }
        $count++
        if ($count % 5000 -eq 0) { Write-Output "Verified $count / $($files.Count): $source" }
    }
    $backup = [IO.Path]::GetFullPath($source + '.studyspace-moving')
    if ($backup -cne ($source + '.studyspace-moving') -or (Test-Path -LiteralPath $backup)) { throw 'Invalid staging path' }
    Move-Item -LiteralPath $source -Destination $backup
    try {
        New-Item -ItemType Junction -Path $source -Target $target | Out-Null
        $link = Get-Item -LiteralPath $source -Force
        if ($link.LinkType -ne 'Junction' -or $link.Target -ne $target) { throw 'Link verification failed' }
    } catch {
        if (!(Test-Path -LiteralPath $source)) { Move-Item -LiteralPath $backup -Destination $source }
        throw
    }
    # Delete only this fixed staging directory after every copied file passes SHA-256.
    if ((Get-Item -LiteralPath $backup -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Staging is a link' }
    Remove-Item -LiteralPath $backup -Recurse -Force
    Write-Output "Migrated: $source -> $target"
}
$variables = @{
    GRADLE_USER_HOME='D:\DevData\Gradle'
    ANDROID_HOME='D:\DevTools\Android\Sdk'
    ANDROID_SDK_ROOT='D:\DevTools\Android\Sdk'
    ANDROID_AVD_HOME='D:\DevData\Android\avd'
    PUB_CACHE='D:\DevTools\PubCache'
}
foreach ($name in $variables.Keys) { [Environment]::SetEnvironmentVariable($name,$variables[$name],'User') }
Get-PSDrive C,D | Select-Object Name,@{N='FreeGB';E={[math]::Round($_.Free/1GB,2)}}
