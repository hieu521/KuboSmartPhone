# Load .env and run NotificationService (PowerShell)
# Chạy từ thư mục gốc project: .\NotificationService\run-with-env.ps1

$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) ".env"
if (-not (Test-Path $envFile)) {
    Write-Error "File .env không tìm thấy tại: $envFile"
    exit 1
}

Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
        Set-Item -Path "Env:$name" -Value $value
    }
}

Set-Location $PSScriptRoot
mvn spring-boot:run
