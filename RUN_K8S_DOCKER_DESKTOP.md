# Chạy lại dự án `Javaspring` trên Docker Desktop Kubernetes (Windows)

Tài liệu này hướng dẫn bạn **chạy lại** stack microservices của repo này trên **Docker Desktop Kubernetes** (không cần Minikube/kind), theo đúng thứ tự bước và **giải thích vì sao** phải làm như vậy.

> Repo deploy bằng **Kustomize**: `kubectl apply -k k8s/`  
> Public traffic đi qua **Ingress Nginx** → `Service apigateway` → downstream services bằng **Kubernetes Service DNS** (không cần Eureka).

---

## 0) Yêu cầu

- **Docker Desktop** đã bật
- **Kubernetes** trong Docker Desktop đã bật
- Có `kubectl` và `helm` trong PATH

Kiểm tra nhanh:

```powershell
kubectl config current-context
kubectl get nodes -o wide
```

- **Vì sao**: `kubectl` phải trỏ đúng cluster (ở máy bạn thường là `docker-desktop`) và node phải `Ready` thì mới deploy được.

---

## 1) (Một lần) Cài Ingress Controller: `ingress-nginx`

Nếu bạn chưa cài `ingress-nginx` thì cài bằng Helm:

```powershell
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx -n ingress-nginx --create-namespace
```

Kiểm tra:

```powershell
kubectl get pods -n ingress-nginx
kubectl get svc -n ingress-nginx
```

- **Vì sao**: Repo có `k8s/ingress.yaml` (host `api.javaspring.local`). Ingress resource **chỉ hoạt động** nếu trong cluster có Ingress Controller.

> Docker Desktop thường map ingress về `localhost`, nên truy cập từ máy host tiện.

---

## 2) Build Docker images cho các service

Chạy từ thư mục gốc repo:

```powershell
.\scripts\k8s-build-images.ps1
```

Kiểm tra image đã có:

```powershell
docker images "javaspring/*" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"
```

- **Vì sao**: Manifest K8s dùng image local tag `javaspring/<service>:latest` và `imagePullPolicy: IfNotPresent`. Nếu chưa build thì pod sẽ không chạy được (ImagePullBackOff).

### Lưu ý quan trọng: `product-service` + `CommonLib`

`ProductService` phụ thuộc module local `CommonLib` (`com.example:CommonLib:0.0.1-SNAPSHOT`).  
Vì vậy `ProductService/Dockerfile` của repo **build từ context repo root** để copy được `CommonLib/` trước khi build `ProductService/`.

Nếu bạn tự build riêng, dùng đúng lệnh:

```powershell
docker build -f ProductService/Dockerfile -t javaspring/product-service:latest .
```

- **Vì sao**: nếu build với context chỉ là `.\ProductService` thì Maven trong container sẽ không tìm thấy artifact `CommonLib`.

---

## 3) Deploy toàn bộ stack lên Kubernetes

Chạy:

```powershell
kubectl apply -k k8s/
```

Kiểm tra:

```powershell
kubectl get ns
kubectl get pods -n javaspring
kubectl get svc -n javaspring
kubectl get ingress -n javaspring
```

- **Vì sao**: `k8s/kustomization.yaml` gom namespace, mysql, redis/rabbitmq, microservices, ingress. Một lệnh `apply -k` đảm bảo các resource được tạo đầy đủ.

### Đợi MySQL sẵn sàng (khuyến nghị)

```powershell
kubectl wait --for=condition=available deployment/mysql -n javaspring --timeout=300s
```

- **Vì sao**: các service phụ thuộc DB/Redis/RabbitMQ. Đợi DB ổn định giúp giảm crash/restart và tránh lỗi kết nối lúc khởi động.

---

## 4) Map host cho Ingress (bắt buộc để gọi đúng hostname)

Ingress dùng host `api.javaspring.local` (xem `k8s/ingress.yaml`).

Mở Notepad **Run as administrator** và sửa file:
`C:\Windows\System32\drivers\etc\hosts`

Thêm dòng:

```text
127.0.0.1 api.javaspring.local
```

- **Vì sao**: Ingress match theo `host`. Nếu không map hosts, request từ browser/curl sẽ không vào đúng rule.

---

## 5) Test nhanh sau khi chạy

### 5.1 Kiểm tra gateway có route

Gateway route chính hiện có (xem `APIGateway/src/main/java/.../RateLimitedRoutesConfig.java`):

- `GET/POST /users/**` → user-service
- `GET/POST /departments/**` → department-service
- `POST /orders/**` → order-service
- `POST /admin/campaigns/**` → promo-service
- `POST /admin/promo-stocks/**` → inventory-service

Test “có trả HTTP” (không đảm bảo business OK):

```powershell
curl -i http://api.javaspring.local/users
```

> Nhiều endpoint cần JWT nên có thể 401/403 là bình thường (nghĩa là routing đã tới đúng service).

### 5.2 Test Swagger (khuyến nghị khi demo)

Một số service có Springdoc. Nếu bạn muốn mở Swagger nhanh (không phụ thuộc Ingress route), dùng `port-forward`:

```powershell
kubectl port-forward -n javaspring svc/auth-service 8083:8083
kubectl port-forward -n javaspring svc/product-service 8086:8086
```

Sau đó mở trình duyệt:

- `http://localhost:8083/swagger-ui/index.html`
- `http://localhost:8086/swagger-ui/index.html`

- **Vì sao**: `apigateway` hiện **không route** tới `auth-service`/`product-service`. Port-forward giúp test trực tiếp service.

---

## 6) Scale để “load balancing” nội bộ (không cần Eureka)

Muốn K8s tự phân phối request qua nhiều pod (load balance nội bộ qua `Service`), bạn tăng replicas:

```powershell
kubectl scale deployment/order-service -n javaspring --replicas=2
kubectl scale deployment/promo-service -n javaspring --replicas=2
kubectl get pods -n javaspring -o wide
```

- **Vì sao**: Trong Kubernetes, `Service` sẽ load-balance tới các pod endpoints. Bạn không cần Eureka để làm LB khi đã dùng K8s Service DNS.

---

## 7) Troubleshooting nhanh

### Pod bị CrashLoopBackOff / Error

```powershell
kubectl get pods -n javaspring
kubectl logs -n javaspring deploy/apigateway --tail=200
kubectl logs -n javaspring deploy/order-service --tail=200
kubectl describe pod -n javaspring <pod-name>
```

### Ingress không vào được

```powershell
kubectl get ingress -n javaspring -o wide
kubectl get pods -n ingress-nginx
```

Kiểm tra lại `hosts` đã map `api.javaspring.local` chưa.

---

## 8) Tắt / gỡ dự án

Gỡ toàn bộ resource của dự án (khuyến nghị):

```powershell
kubectl delete -k k8s/
```

- **Vì sao**: xóa đúng những gì repo đã apply (namespace, deployments, services, ingress, pvc…).

> Lưu ý: các pod hệ thống `kube-system/*` và `ingress-nginx/*` vẫn chạy nếu bạn **vẫn bật Kubernetes** trong Docker Desktop. Muốn “không còn container k8s”, tắt Kubernetes trong Docker Desktop Settings.

