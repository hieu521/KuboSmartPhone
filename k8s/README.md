# Phone Store Microservices Project
# Du an Microservice Ban Dien Thoai

---

## VIETNAMESE

## 1) Mo dau - du an nay lam gi?

Day la he thong backend microservices cho bai toan ban dien thoai online.  
Ung dung duoc tach theo service de de bao tri, de scale, va de trien khai len Kubernetes.

### Cac service chinh

- `APIGateway`: cong vao duy nhat, route request, rate-limit.
- `AuthService`: dang ky, dang nhap, JWT access token + refresh token.
- `UserService`: thong tin nguoi dung.
- `ProductService`: du lieu san pham.
- `InventoryService`: ton kho.
- `OrderService`: dat hang (single item + cart), dedup, xu ly dat hang an toan.
- `PromoService`: chuong trinh khuyen mai.
- `NotificationService`: gui thong bao/email.
- `DepartmentService`: nhom/phan loai bo sung cho nghiep vu.

## 2) Kien truc va communication

- API ben ngoai vao qua `Ingress (nginx)` -> `APIGateway` -> service noi bo.
- Service-to-service dung HTTP noi bo (Service DNS trong Kubernetes).
- Xu ly bat dong bo qua `RabbitMQ`.
- Cache/dedup/lock qua `Redis`.
- Du lieu chinh luu MySQL.

## 3) RabbitMQ trong du an dung de lam gi?

- Tach request dong bo khoi xu ly hau truong.
- Giam coupling giua service (service A khong can doi service B xu ly xong ngay).
- Co the retry / scale consumer rieng.

Vi du:
- Order tao su kien -> Notification nhan va gui email/thong bao.
- Cac queue trong he thong duoc cau hinh qua env (nhu `order.created`, `order.result`, `campaign.applied`...).

## 4) Redis trong du an dung de lam gi?

- Luu du lieu tam/co TTL (token/cache).
- Ho tro rate-limit tai Gateway.
- Ho tro dedup va xu ly dat hang tranh trung request.
- Tang toc do xu ly voi data hot.

## 5) Configuration overview (tong quan cau hinh)

### 5.1 Cau hinh app theo service

- Moi service dung `src/main/resources/application.properties` hoac `application.yml`.
- Uu tien doc gia tri tu bien moi truong (env vars), co default fallback.

### 5.2 Cau hinh Kubernetes

- Thu muc `k8s/` chua manifest goc:
  - `namespace.yaml`
  - `mysql.yaml`, `redis-rabbitmq.yaml`
  - `microservices.yaml`
  - `ingress.yaml`
  - `kustomization.yaml`

- Overlay Docker Hub:
  - `k8s/overlays/dockerhub/kustomization.yaml`
  - doi image `javaspring/...` -> `<dockerhub-user>/<repo>:<service>-<tag>`

### 5.3 ConfigMap + Secret (quan trong)

`NotificationService` da duoc setup theo chuan K8s:

- ConfigMap: `notification-mail-config`
  - host/port/mail flags khong nhay cam.
- Secret: `notification-mail-secret`
  - username/password SMTP (nhay cam).
- Pod inject env qua `envFrom`.

=> Khong can nhung `.env` vao image Docker.

## 6) Cac file can chinh khi deploy len K8s

### Bat buoc

1. `k8s/overlays/dockerhub/kustomization.yaml`
   - Cap nhat tag image theo version moi.
2. `k8s/microservices.yaml`
   - Neu can doi env, ports, resource, replica.
3. `k8s/notification-mail-configmap.yaml`
   - Cap nhat mail host/port/auth/starttls neu can.

### Neu co secret moi

Tao/refresh secret tu file env local:

```powershell
kubectl create secret generic notification-mail-secret `
  -n javaspring `
  --from-env-file=.env `
  --dry-run=client -o yaml | kubectl apply -f -
```

## 7) Quy trinh deploy len Docker Hub + K8s

### 7.1 Build va push image

```powershell
cd <repo-path>
docker login

# Vi du 1 service:
docker build -t javaspring/order-service:latest .\OrderService
docker tag javaspring/order-service:latest <dockerhub-user>/<repo>:order-service-<tag>
docker push <dockerhub-user>/<repo>:order-service-<tag>
```

Co the dung script da co:

- `.\scripts\docker-hub-push.ps1 -Tag <tag>`
- `.\scripts\minikube-dockerhub.ps1 -Tag <tag>`

### 7.2 Apply len K8s

```powershell
kubectl apply -k k8s/
```

Hoac overlay Docker Hub:

```powershell
.\scripts\k8s-apply-dockerhub.ps1
```

### 7.3 Rollout 1 service sau khi push image moi

```powershell
kubectl rollout restart deployment/<service-name> -n javaspring
kubectl rollout status deployment/<service-name> -n javaspring --timeout=300s
```

## 8) Cach chay tren Minikube

```powershell
minikube start
minikube addons enable ingress
```

Neu truy cap host local:

```powershell
minikube tunnel
```

Hosts file (admin):

```text
127.0.0.1 api.javaspring.local
```

Swagger sample:

- Auth: `http://api.javaspring.local/auth/swagger-ui.html`
- Order: `http://api.javaspring.local/orders/swagger-ui/index.html`

## 9) Kiem tra da deploy thanh cong chua

```powershell
kubectl get pods -n javaspring
kubectl get svc -n javaspring
kubectl get ingress -n javaspring
```

Neu pod READY phan lon la `1/1` va ingress co host -> he thong da len.

---

## ENGLISH

## 1) What this project does

This project is a phone-store backend built with Spring microservices.  
It splits business domains into independent services to improve maintainability, scalability, and Kubernetes deployment.

### Main services

- `APIGateway`: single entry point, routing, rate limiting.
- `AuthService`: login/register, JWT access token, refresh token.
- `UserService`: user profile data.
- `ProductService`: product data.
- `InventoryService`: stock management.
- `OrderService`: order placement (single and cart), dedup-safe flow.
- `PromoService`: promotion/campaign logic.
- `NotificationService`: notifications and email.
- `DepartmentService`: additional domain grouping.

## 2) Architecture and communication

- External traffic: `Ingress (nginx)` -> `APIGateway` -> internal services.
- Internal service communication: Kubernetes Service DNS.
- Async workflows: RabbitMQ.
- Cache/rate-limit/dedup support: Redis.
- Persistent storage: MySQL.

## 3) RabbitMQ usage in this project

- Decouples synchronous API calls from background processing.
- Helps isolate failures and scale consumers independently.
- Supports event-driven flows (order events, campaign events, notifications).

## 4) Redis usage in this project

- Fast in-memory data and TTL-based storage.
- Gateway distributed rate limiting.
- Request dedup/idempotency support for order flows.

## 5) Configuration overview

### App level

- Each service uses `application.properties`/`application.yml`.
- Environment variables override defaults per environment.

### Kubernetes level

- Base manifests under `k8s/`.
- Docker Hub overlay under `k8s/overlays/dockerhub/`.

### ConfigMap + Secret

`NotificationService` is configured to read mail settings using:

- ConfigMap: non-sensitive mail settings.
- Secret: SMTP credentials.
- Injected into Pod with `envFrom`.

## 6) Files to adjust before deployment

- `k8s/overlays/dockerhub/kustomization.yaml` (image tags).
- `k8s/microservices.yaml` (env/resources/replicas when needed).
- `k8s/notification-mail-configmap.yaml` (mail host/port flags).
- Secret refresh command from local `.env` (for credentials).

## 7) Docker Hub + Kubernetes deployment flow

```powershell
cd <repo-path>
docker login
docker build -t javaspring/<service>:latest .\<ServiceFolder>
docker tag javaspring/<service>:latest <dockerhub-user>/<repo>:<service>-<tag>
docker push <dockerhub-user>/<repo>:<service>-<tag>
kubectl rollout restart deployment/<service> -n javaspring
kubectl rollout status deployment/<service> -n javaspring --timeout=300s
```

Apply full manifests:

```powershell
kubectl apply -k k8s/
```

or Docker Hub overlay:

```powershell
.\scripts\k8s-apply-dockerhub.ps1
```

## 8) Minikube quick run

```powershell
minikube start
minikube addons enable ingress
minikube tunnel
```

Add hosts mapping:

```text
127.0.0.1 api.javaspring.local
```

## 9) Deployment health checks

```powershell
kubectl get pods -n javaspring
kubectl get svc -n javaspring
kubectl get ingress -n javaspring
```

If pods are mostly `1/1 Running` and ingress is healthy, deployment is ready.

