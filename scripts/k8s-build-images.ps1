# Build toàn bộ image Docker cho triển khai K8s (tag javaspring/*:latest).
# Chạy từ thư mục gốc repo: .\scripts\k8s-build-images.ps1
# Minikube: chạy `minikube docker-env` rồi build để image nằm trong node.

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$images = @(
    @{ Name = "eureka-service"; Path = "EurekaService" },
    @{ Name = "apigateway"; Path = "APIGateway" },
    @{ Name = "auth-service"; Path = "AuthService" },
    @{ Name = "user-service"; Path = "UserService" },
    @{ Name = "department-service"; Path = "DepartmentService" },
    @{ Name = "product-service"; Path = "ProductService" },
    @{ Name = "inventory-service"; Path = "InventoryService" },
    @{ Name = "order-service"; Path = "OrderService" },
    @{ Name = "promo-service"; Path = "PromoService" },
    @{ Name = "notification-service"; Path = "NotificationService" }
)

foreach ($img in $images) {
    $tag = "javaspring/$($img.Name):latest"
    Write-Host "Building $tag ..."
    $svcPath = Join-Path $root $img.Path
    # ProductService depends on ../CommonLib; image paths are relative to repo root.
    if ($img.Name -eq "product-service") {
        docker build -f (Join-Path $svcPath "Dockerfile") -t $tag $root
    } else {
        docker build -t $tag $svcPath
    }
}

Write-Host "Done. Images: javaspring/*:latest"
