$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env.local'
$source = 'không có'

if (![string]::IsNullOrWhiteSpace($env:GEMINI_API_KEY)) {
    $source = 'biến môi trường'
} elseif (Test-Path -LiteralPath $envFile) {
    $entry = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^\s*GEMINI_API_KEY\s*=' } | Select-Object -Last 1
    if ($entry -match '^\s*GEMINI_API_KEY\s*=\s*(.*?)\s*$') {
        $value = $Matches[1].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        if (![string]::IsNullOrWhiteSpace($value)) {
            $env:GEMINI_API_KEY = $value
            $source = 'file .env.local'
        }
    }
}

if ([string]::IsNullOrWhiteSpace($env:GEMINI_API_KEY) -and ![string]::IsNullOrWhiteSpace($env:STUDYSPACE_GEMINI_API_KEY)) {
    $env:GEMINI_API_KEY = $env:STUDYSPACE_GEMINI_API_KEY
    $source = 'biến môi trường'
}

# Spring Boot maps this legacy environment name directly to the property and
# would let its stale value override the canonical key loaded above.
Remove-Item Env:STUDYSPACE_GEMINI_API_KEY -ErrorAction SilentlyContinue

$env:STUDYSPACE_GEMINI_KEY_SOURCE = $source
return $source
