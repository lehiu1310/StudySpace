$secrets = Import-Clixml -LiteralPath (Join-Path $PSScriptRoot '.local-secrets.xml')
Write-Output 'Username: admin'
Write-Output ([Net.NetworkCredential]::new('', $secrets.Admin).Password)
