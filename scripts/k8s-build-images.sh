#!/usr/bin/env bash
# Build toàn bộ image Docker cho K8s (tag javaspring/*:latest).
# Chạy từ thư mục gốc repo: ./scripts/k8s-build-images.sh
# Minikube: eval $(minikube docker-env) rồi chạy script để image nằm trong node.

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

images=(
  "eureka-service:EurekaService"
  "apigateway:APIGateway"
  "auth-service:AuthService"
  "user-service:UserService"
  "department-service:DepartmentService"
  "product-service:ProductService"
  "inventory-service:InventoryService"
  "order-service:OrderService"
  "promo-service:PromoService"
  "notification-service:NotificationService"
)

for entry in "${images[@]}"; do
  name="${entry%%:*}"
  path="${entry#*:}"
  tag="javaspring/${name}:latest"
  echo "Building ${tag} ..."
  docker build -t "${tag}" "${ROOT}/${path}"
done

echo "Done. Images: javaspring/*:latest"
