# Tag và push image local javaspring/*:latest lên Docker Hub (một repo, nhiều tag).
# Mỗi service là một tag: <user>/<repo>:<service>-<version>  ví dụ santoskubo/javaspringboot:apigateway-v1
#
# Trước khi chạy:
#   1) docker login   (đăng nhập Docker Hub)
#   2) .\scripts\k8s-build-images.ps1
#
# Chạy từ thư mục gốc repo:
#   .\scripts\docker-hub-push.ps1
#   .\scripts\docker-hub-push.ps1 -Tag v2 -IncludeEureka
#   .\scripts\docker-hub-push.ps1 -Tag v2 -SyncOverlay   # cập nhật newTag trong k8s/overlays/dockerhub/kustomization.yaml
#
param(
    [string] $DockerHubUser = "santoskubo",
    [string] $Repository = "javaspringboot",
    [string] $Tag = "v1",
    [switch] $IncludeEureka,
    [switch] $SkipPush,
    [switch] $SyncOverlay
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$fullRepo = "${DockerHubUser}/${Repository}"
$images = @(
    "apigateway",
    "auth-service",
    "user-service",
    "department-service",
    "product-service",
    "inventory-service",
    "order-service",
    "promo-service",
    "notification-service"
)

if ($IncludeEureka) {
    $images = @("eureka-service") + $images
}

Write-Host "Destination: ${fullRepo}:<service>-${Tag}"
Write-Host "Ensure 'docker login' is done. SkipPush=$SkipPush`n"

foreach ($name in $images) {
    $local = "javaspring/${name}:latest"
    $remote = "${fullRepo}:${name}-${Tag}"
    Write-Host "docker tag $local -> $remote"
    docker tag $local $remote
    if (-not $SkipPush) {
        docker push $remote
    }
}

if ($SyncOverlay) {
    $ku = Join-Path $root "k8s\overlays\dockerhub\kustomization.yaml"
    if (-not (Test-Path $ku)) { throw "Không tìm thấy $ku" }
    $microKustomizeSvcs = @(
        "apigateway", "auth-service", "user-service", "department-service", "product-service",
        "inventory-service", "order-service", "promo-service", "notification-service"
    )
    $text = [System.IO.File]::ReadAllText($ku)
    foreach ($svc in $microKustomizeSvcs) {
        $text = $text -replace "newTag: $svc-[^\r\n]+", "newTag: ${svc}-$Tag"
    }
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($ku, $text.TrimEnd() + "`n", $utf8NoBom)
    Write-Host "Đã cập nhật newTag (*-$Tag) trong k8s/overlays/dockerhub/kustomization.yaml"
}

Write-Host "`nDone. Triển khai K8s từ Hub: .\scripts\k8s-apply-dockerhub.ps1"
if (-not $SyncOverlay) {
    Write-Host "(Hoặc chạy lại với -SyncOverlay để sửa newTag trong overlay cho khớp -Tag $Tag)"
}
