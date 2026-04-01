# Áp manifest K8s dùng image Docker Hub (overlay dockerhub).
# Cần: kubectl, và đã push image (.\scripts\docker-hub-push.ps1).
#
# kubectl mặc định chặn file ngoài thư mục overlay — dùng --load-restrictor LoadRestrictionsNone.

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$overlay = Join-Path $root "k8s\overlays\dockerhub"

kubectl kustomize $overlay --load-restrictor LoadRestrictionsNone | kubectl apply -f -
Write-Host "Done."
