# Kubernetes + Nginx Ingress cho Javaspring

Tài liệu này mô tả cách chạy stack microservice trên Kubernetes (Minikube / Docker Desktop / cluster), với **Ingress (nginx)** làm cổng L7 phía ngoài và **Spring Cloud Gateway** làm cổng nghiệp vụ phía trong.

## Vai trò từng lớp

| Lớp | Vai trò |
|-----|--------|
| **Ingress (nginx)** | Hostname/reverse proxy vào cluster, tùy chọn TLS/WAF/size limit |
| **API Gateway** | Route theo path (`/orders/**`…), rate limit, proxy HTTP tới URL backend (Kubernetes Service DNS) |
| **Eureka** | *(tùy chọn, mặc định tắt trong manifest)* registry — có thể bật lại file `eureka.yaml` + dependency trong `pom` |
| **Microservices** | Nghiệp vụ |

**Ghi chú:** Gateway không còn dùng `lb("service")` + Eureka; backend là biến môi trường `APP_GATEWAY_DOWNSTREAM_*` (trong cluster trỏ `http://<service-name>:<port>`). Code Eureka cũ được **comment** trong `pom` / `application.properties`, không xóa.

## Điều kiện

- `kubectl` và một cluster (Minikube, kind, k3s, Docker Desktop Kubernetes)
- Docker để build image
- **Ingress Controller**: ví dụ [ingress-nginx](https://kubernetes.github.io/ingress-nginx/deploy/)

### Cài Ingress Nginx (ví dụ Minikube)

```bash
minikube addons enable ingress
```

Hoặc Helm:

```bash
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx -n ingress-nginx --create-namespace
```

Sau đó `ingressClassName: nginx` trong `ingress.yaml` sẽ khớp.

## Build image

Từ thư mục gốc repo:

**Windows (PowerShell):**

```powershell
.\scripts\k8s-build-images.ps1
```

> Nếu PowerShell báo `... is not recognized ... but does exist in the current location`, hãy nhớ chạy script với `.\` (PowerShell không tự chạy file ở thư mục hiện tại).

**Linux / macOS (Bash):**

```bash
chmod +x scripts/k8s-build-images.sh
./scripts/k8s-build-images.sh
```

**Minikube** (image phải nằm trong Docker của Minikube):

```powershell
minikube docker-env | Invoke-Expression
.\scripts\k8s-build-images.ps1
```

```bash
eval $(minikube docker-env)
./scripts/k8s-build-images.sh
```

Trong manifest, `imagePullPolicy: IfNotPresent` dùng image local.

### Chạy trên Linux — lưu ý ngắn

- `kubectl`, `docker` (hoặc `podman` + tương thích), cluster: cài như bình thường trên distro (apt/dnf/snap).
- Manifest `k8s/*.yaml` **không phụ thuộc Windows**; `kubectl apply -k k8s/` dùng giống hệt.
- Thêm host cho Ingress: `sudo sh -c 'echo "<IP_INGRESS> api.javaspring.local" >> /etc/hosts'` (IP lấy từ `minikube ip`, `kubectl get ingress -n javaspring`, hoặc IP node có Ingress Controller).
- Nginx vẫn là **Ingress Controller trong cluster** (không có file `nginx.conf` trong repo); trên Linux bạn chỉ cần cài addon/Helm như README.

## Áp manifest

**Một lệnh (Kustomize):**

```bash
kubectl apply -k k8s/
```

**Hoặc từng file (thứ tự gợi ý):**

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/mysql-init-configmap.yaml
kubectl apply -f k8s/mysql.yaml
kubectl apply -f k8s/redis-rabbitmq.yaml
# Đợi MySQL Ready (init DB lần đầu có thể ~1–2 phút)
kubectl wait --for=condition=available deployment/mysql -n javaspring --timeout=300s

# Eureka (tùy chọn): kubectl apply -f k8s/eureka.yaml — mặc định không cần (Gateway dùng URL Service K8s)

kubectl apply -f k8s/microservices.yaml
kubectl apply -f k8s/ingress.yaml
```

Kiểm tra:

```bash
kubectl get pods -n javaspring
kubectl get ingress -n javaspring
```

## Truy cập API

Ingress dùng host `api.javaspring.local` (xem `ingress.yaml`). Thêm vào hosts:

- **Minikube**: `minikube ip` → map `api.javaspring.local` tới IP đó
- **Docker Desktop**: thường là `127.0.0.1 api.javaspring.local`

Trên Windows:

1. Mở file `C:\Windows\System32\drivers\etc\hosts` bằng quyền Administrator.
2. Thêm dòng:
   ```text
   127.0.0.1 api.javaspring.local
   ```
3. Chạy `ipconfig /flushdns`.

Gọi thử:

```bash
curl -s http://api.javaspring.local/actuator  # có thể 404 nếu không bật actuator
curl -s http://api.javaspring.local/orders -X POST ...
```

(Thực tế cần JWT; dùng Swagger qua gateway nếu bật springdoc trên từng service.)

## HTTPS (tùy chọn)

- Dùng **cert-manager** + Let's Encrypt, thêm annotation TLS trên Ingress
- Hoặc terminate TLS tại **Nginx ngoài cluster** và chỉ proxy HTTP vào Ingress nội bộ

> Mặc định manifest hiện tại chỉ cấu hình HTTP (không có `spec.tls` trong `k8s/ingress.yaml`).

## MySQL

- Một Pod MySQL + PVC; script init tạo **6 database + user** giống môi trường Docker Compose
- Nếu cần reset DB: xóa PVC `mysql-pvc` và deploy lại (mất dữ liệu)

## Ghi chú

- **ConfigServer** không nằm trong manifest mặc định; các service hiện không bắt buộc config server để chạy
- **NotificationService**: `SPRING_MAIL_HOST=localhost` trong manifest chỉ để app khởi động; gửi mail thật cần SMTP (Mailtrap, SES, …) qua biến môi trường

## Troubleshooting

- Pod `CrashLoopBackOff`: `kubectl logs -n javaspring deploy/order-service` (ví dụ)
- Gateway 502/503 tới backend: kiểm tra biến `APP_GATEWAY_DOWNSTREAM_*` trên Deployment `apigateway` và Pod backend đã Ready
- Image không tìm thấy: build lại trong đúng Docker context (Minikube docker-env)
- Truy cập `api.javaspring.local` ra trang IIS/404 `C:\inetpub\wwwroot`: port 80 đang do IIS chiếm.
  - Tạm dừng IIS để test Ingress:
    ```powershell
    Stop-Service W3SVC -Force
    Stop-Service WAS -Force
    ```
  - Bật lại IIS khi cần:
    ```powershell
    Start-Service WAS
    Start-Service W3SVC
    ```
