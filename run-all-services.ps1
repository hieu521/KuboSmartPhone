# Chay tat ca services de test (Auth, Product, Inventory, Order, Promo)
# Docker Compose phai dang chay truoc: docker compose up -d
# Chay tu thu muc goc: .\run-all-services.ps1

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

Write-Host "Kiem tra Docker (redis, rabbitmq, mysql)..." -ForegroundColor Cyan
docker compose ps 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Chay docker compose truoc: docker compose up -d" -ForegroundColor Red
    exit 1
}

function Start-ServiceInWindow {
    param([string]$Name, [string]$Path)
    $fullPath = Join-Path $root $Path
    if (-not (Test-Path $fullPath)) {
        Write-Host "Skip $Name (not found)" -ForegroundColor Yellow
        return
    }
    Write-Host "Mo cua so: $Name" -ForegroundColor Green
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$fullPath'; Write-Host '$Name' -ForegroundColor Cyan; mvn spring-boot:run"
    Start-Sleep -Seconds 5
}

Start-ServiceInWindow "AuthService"      "AuthService"
Start-ServiceInWindow "ProductService"   "ProductService"
Start-ServiceInWindow "InventoryService" "InventoryService"
Start-ServiceInWindow "OrderService"     "OrderService"
Start-ServiceInWindow "PromoService"     "PromoService"

Write-Host ""
Write-Host "=== Swagger UI (test API) ===" -ForegroundColor Green
Write-Host "  Auth:         http://localhost:8083/swagger-ui.html"
Write-Host "  Product:      http://localhost:8086/swagger-ui.html"
Write-Host "  Inventory:    http://localhost:8087/swagger-ui.html"
Write-Host "  Order:        http://localhost:8088/swagger-ui.html"
Write-Host "  Promo:        http://localhost:8085/swagger-ui.html"
Write-Host "  Notification: http://localhost:8084/swagger-ui.html (Docker)"
Write-Host ""
Write-Host "Seed: admin@example.com / admin123 | user@example.com / user123" -ForegroundColor Yellow
Write-Host "Promo ID: BF2026"
