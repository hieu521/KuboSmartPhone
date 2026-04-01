# Minikube (không cần bật K8s trên Docker Desktop) + build + push Docker Hub + apply overlay.
#
# Điều kiện: cài Minikube + kubectl + Docker; đăng nhập Hub: docker login
#
# Luồng: image build trên Docker máy bạn (javaspring/*:latest) → push Hub → cluster Minikube kéo từ Hub.
#        Không cần `minikube docker-env` cho luồng này.
#
# Chạy từ thư mục gốc repo:
#   .\scripts\minikube-dockerhub.ps1
#   .\scripts\minikube-dockerhub.ps1 -Tag v2
#   .\scripts\minikube-dockerhub.ps1 -SkipBuild   # đã build & push, chỉ start minikube + apply
#
param(
    [string] $Tag = "v1",
    [string] $DockerHubUser = "santoskubo",
    [string] $Repository = "javaspringboot",
    [switch] $SkipMinikube,
    [switch] $SkipIngressAddon,
    [switch] $SkipBuild,
    [switch] $SkipPush,
    [switch] $SkipApply
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Get-Command minikube -ErrorAction SilentlyContinue)) {
    throw "Chưa có minikube trong PATH. Cài: https://minikube.sigs.k8s.io/docs/start/"
}
if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw "Chưa có kubectl trong PATH."
}

if (-not $SkipMinikube) {
    $st = minikube status 2>&1
    $running = $LASTEXITCODE -eq 0
    if (-not $running) {
        Write-Host "Đang khởi động minikube..."
        minikube start
    } else {
        Write-Host "Minikube đã chạy."
    }
    kubectl config use-context minikube | Out-Null
}

if (-not $SkipIngressAddon) {
    Write-Host "Bat addon ingress (nginx)..."
    minikube addons enable ingress
}

if (-not $SkipBuild) {
    & "$PSScriptRoot\k8s-build-images.ps1"
}

if (-not $SkipPush) {
    & "$PSScriptRoot\docker-hub-push.ps1" -Tag $Tag -DockerHubUser $DockerHubUser -Repository $Repository -SyncOverlay
}

if (-not $SkipApply) {
    & "$PSScriptRoot\k8s-apply-dockerhub.ps1"
    Write-Host "Cho MySQL (lan dau co the 1-2 phut)..."
    kubectl wait --for=condition=available deployment/mysql -n javaspring --timeout=300s 2>$null
}

$ip = (minikube ip).Trim()
Write-Host ""
Write-Host "=== Vao API tren Windows (Minikube Docker driver) ===" -ForegroundColor Cyan
Write-Host "  Neu http://api.javaspring.local/ bi timeout: IP minikube ($ip) thuong khong vao duoc tu may host."
Write-Host "  Chay terminal 2 (giu mo):"
Write-Host "    .\scripts\minikube-access.ps1"
Write-Host "  Sua hosts (Admin):  127.0.0.1  api.javaspring.local   (xoa dong cu $ip neu co)"
Write-Host "  Trinh duyet:  http://api.javaspring.local:18080/  (.\scripts\minikube-access.ps1 — mac dinh 18080)"
Write-Host ""
Write-Host "=== Hosts file ===" -ForegroundColor Cyan
Write-Host "  C:\Windows\System32\drivers\etc\hosts"
Write-Host "Neu port 80 bi IIS chiem: Stop-Service W3SVC -Force; Stop-Service WAS -Force" -ForegroundColor Yellow
Write-Host "Kiem tra: kubectl get pods -n javaspring"
Write-Host "Done."
