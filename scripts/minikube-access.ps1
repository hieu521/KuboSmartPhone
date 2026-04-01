# Minikube tren Windows (Docker driver): minikube IP + Ingress thuong kho vao tu may host.
#
# MAC DINH: port-forward THANG VAO APIGATEWAY (tranh loi Host: api.javaspring.local:8080 khong khop Ingress).
# Tuy chon -ViaIngress: forward vao ingress-nginx (can host dung api.javaspring.local, khong kem :port neu gap 404).
#
# Chay:
#   .\scripts\minikube-access.ps1
# Trinh duyet: http://api.javaspring.local:18080/auth/swagger-ui.html
# (Mac dinh 18080 de tranh dung port 8080 voi Apache/XAMPP — neu curl ra Server: Apache + 404 thi dung sai dich.)
# Muon dung 8080: tat Apache hoac:  .\scripts\minikube-access.ps1 -LocalPort 8080
#
# Hosts: 127.0.0.1  api.javaspring.local

param(
    [int] $LocalPort = 18080,
    [switch] $ViaIngress
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw "kubectl not found in PATH."
}

$ns = "javaspring"
$svc = "apigateway"
$targetPort = 8080

if ($ViaIngress) {
    $ns = "ingress-nginx"
    $svc = "ingress-nginx-controller"
    $targetPort = 80
}

$exists = kubectl get svc -n $ns $svc -o name 2>$null
if (-not $exists) {
    if ($ViaIngress) {
        throw "Khong tim thay $ns/$svc. Chay: minikube addons enable ingress"
    }
    throw "Khong tim thay $ns/$svc. Kiem tra: kubectl get svc -n javaspring"
}

Write-Host ""
Write-Host "=== Minikube access (Windows) ===" -ForegroundColor Cyan
if ($ViaIngress) {
    Write-Host "Port-forward: ingress-nginx/$svc -> 127.0.0.1:${LocalPort} (HTTP vao Ingress)"
    Write-Host "Neu Swagger 404, thu lai KHONG -ViaIngress (mac dinh forward apigateway)." -ForegroundColor Yellow
} else {
    Write-Host "Port-forward: javaspring/apigateway -> 127.0.0.1:${LocalPort} (truc tiep Gateway)"
}
Write-Host ""
Write-Host "1) Hosts (Admin): 127.0.0.1  api.javaspring.local" -ForegroundColor Yellow
Write-Host ""
Write-Host "2) Swagger Auth: http://api.javaspring.local:${LocalPort}/auth/swagger-ui.html" -ForegroundColor Yellow
Write-Host "   Gateway doc:   http://api.javaspring.local:${LocalPort}/gateway/swagger-ui.html" -ForegroundColor Yellow
Write-Host ""
Write-Host "3) Giu cua so PowerShell nay MO (Ctrl+C de dung)." -ForegroundColor Yellow
Write-Host ""

kubectl port-forward -n $ns svc/$svc "${LocalPort}:${targetPort}"
