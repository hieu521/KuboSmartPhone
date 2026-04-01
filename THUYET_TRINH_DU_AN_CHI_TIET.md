# Kịch bản thuyết trình dự án (CHI TIẾT theo đúng em đã làm) - 5–8 phút

## 0) Em làm dự án này để giải quyết vấn đề gì?
Trong flash-sale, lưu lượng tăng đột biến, nên các vấn đề thường gặp là:
- Trừ tồn kho bị sai do nhiều request đồng thời (race condition).
- Người dùng bấm “đặt hàng” nhiều lần làm trừ kho nhiều lần.
- Đợi DB quá lâu làm API chậm.

Vì vậy em thiết kế luồng Order theo hướng:
1. **Redis + Lua atomic** để xử lý “check + trừ + chống trùng” trong 1 lần EVAL (atomic theo nghĩa Redis chạy lệnh/script tuần tự trong 1 thread).
   - Redis lưu stock flash-sale theo key dạng `promo:stock:{promoId}:{productId}` (giá trị là số lượng hiện tại).
   - Redis còn lưu chống đặt trùng bằng key `order:dedup:{promoId}:{userId}` (giá trị là `orderDraftId` đã tạo trong “promo window”, đồng thời có TTL để hết hạn tự động).
   - Khi tạo draft, Redis đánh dấu trạng thái đang chờ xác nhận bằng `order:pending:{orderDraftId}` (cũng có TTL, giúp hệ thống biết draft đang “đang được xử lý” trong thời gian chờ confirm).
   - Lua script thực hiện chuỗi thao tác trong 1 EVAL: (1) kiểm tra dedup có tồn tại không, (2) đọc stock, (3) nếu còn hàng thì `decrby` trừ 1 đơn, (4) set dedup + pending với `EX ttlSeconds`.
   - Lua trả về `status` kèm dữ liệu để OrderService ra quyết định: `OK` (tạo được draft), `DUPLICATE` (đã đặt rồi trong cửa sổ), hoặc `OUT_OF_STOCK` (hết hàng).
2. **RabbitMQ** để tách các bước xử lý “nặng/không cần trả response ngay” khỏi luồng API, giúp giảm thời gian phản hồi và tăng khả năng mở rộng.
   - Có **2 luồng chính** trong dự án:
     1) Luồng Order confirm (đảm bảo đúng tồn kho -> ghi DB):
        - Producer: khi Redis trả `OK`, `OrderPlacementService` gửi `OrderCreatedMessage` vào queue `order.created` (cấu hình qua `app.rabbit.order-created`).
        - Queue được khai báo **durable** bằng `new Queue(queueName, true)` trong `OrderService` để message không bị mất khi restart.
        - Consumer: `OrderCreatedListener` nhận message, tạo/tìm `OrderEntity` theo `orderDraftId`, set `status=CONFIRMED`, `confirmedAt`, rồi `orderRepository.save(...)`.
        - Sau đó gửi `OrderResultMessage` sang queue `order.result` (durable).
        - API thread: `OrderService` dùng `OrderAwaiter` để chờ `OrderResultMessage` theo `orderDraftId` rồi mới trả response cho client.
     2) Luồng Black Friday notification (campaign áp dụng):
        - Admin trigger ở `PromoService` gọi `AuthService` lấy danh sách user theo role, rồi chia chunk.
        - `PromoService` publish `CampaignAppliedMessage` vào queue `campaign.applied` (routing key chính là tên queue).
        - `NotificationService` lắng nghe `campaign.applied` bằng `@RabbitListener`, sau đó:
          - push WebSocket đến đúng userId,
          - gửi mail Black Friday (demo) bằng `MailService`.
3. **MySQL** để lưu trạng thái order sau khi confirm.
4. **API Gateway + Eureka** để có định tuyến + load balancing theo service name.

## 1) Service Registry (Eureka) — Em cấu hình gì? Mục đích làm gì?
### 1.1 Em cấu hình gì
Trong mỗi service (Auth, Product, Inventory, Order, Promo, …) em cấu hình:
- `eureka.client.service-url.defaultZone=http://localhost:8761/eureka/`

=> Tức là khi service chạy lên, nó biết Eureka ở đâu để đăng ký.

### 1.2 Mục đích làm gì
- Không hard-code `localhost:8088` trong Gateway hoặc trong code gọi service.
- Cho phép gateway/service-to-service lấy danh sách instance theo **tên service**.

### 1.3 Dữ liệu chạy ra sao
Khi service khởi động:
1. Service gửi request đăng ký với Eureka.
2. Eureka lưu dữ liệu:
   - `order-service` -> `[host1:port, host2:port, ...]`

Về mặt “dữ liệu”, Eureka chính là nơi lưu “bản đồ định tuyến theo service-name”.

### 1.4 Eureka dùng trong đồ án này cụ thể thế nào?
Trong dự án của em:
- **API Gateway** dùng Spring Cloud LoadBalancer (`lb("order-service")`) để “resolve” service name thành instance thật dựa trên dữ liệu đang lưu trong Eureka.
- Từ đó Gateway forward request tới instance được chọn thay vì phải biết trước IP/port.

(*Lưu ý khi nói: một số call service-to-service trong dự án đang dùng base-url hard-code theo `application.properties` (ví dụ OrderService gọi ProductService bằng `app.product-service.base-url`). Nhưng phần load balancing/định tuyến theo service-name là ở Gateway, và Gateway lấy instance từ Eureka.*)

## 2) API Gateway — Em cấu hình gì? Mục đích làm gì? Load balancing nằm ở đâu?
### 2.1 Em cấu hình gì
Trong `APIGateway/src/main/java/com/example/api/RateLimitedRoutesConfig.java`, em cấu hình route cho các API.

Các route chính mà em rate-limit + load-balance là:
- `GET/POST "/users/**"` -> `lb("user-service")`
- `GET/POST "/departments/**"` -> `lb("department-service")`
- `POST "/orders/**"` -> `lb("order-service")`
- `POST "/admin/campaigns/**"` -> `lb("promo-service")`
- `POST "/admin/promo-stocks/**"` -> `lb("inventory-service")`

### 2.2 Mục đích làm gì
- **Rate limiting**: chặn request quá dày để giảm quá tải.
- **Load balancing**: phân phối request tới nhiều instance của service tương ứng (nếu bạn scale lên nhiều instance).

### 2.3 Luồng chạy request cụ thể
1. Client gọi `APIGateway` (port `8080`).
2. Gateway bắt request vào route `/orders/**`.
3. Gateway áp rate limit theo IP.
4. Gateway dùng `lb("order-service")`:
   - đọc danh sách instance của `order-service` từ Eureka
   - chọn instance phù hợp
5. Gateway forward request sang instance OrderService được chọn.

=> Nên khi hỏi “dự án có load balancing không?”:
**Có. Load balancing thể hiện ở API Gateway nhờ `lb(...)` + Eureka.**

## 3) Centralized configuration (Config Server) — Em cấu hình gì? Mục đích làm gì?
### 3.1 Em cấu hình gì
Config Server chạy tại:
- `server.port=8888`
- `spring.profiles.active=native`
- `spring.cloud.config.server.native.search-locations=classpath:/config`

Trong folder `ConfigServer/src/main/resources/config` có `global.properties` (cấu hình dùng chung).

### 3.2 Mục đích làm gì
- Chuẩn hóa cấu hình chung cho nhiều service.
- Khi cần thay đổi timezone/log level… chỉ đổi ở một nơi.

### 3.3 Dữ liệu chạy ra sao
Với cơ chế centralized config (đúng theo ý đồ đồ án):
- Service khi import config sẽ lấy giá trị từ Config Server.

(*Nếu lúc demo bạn đang chạy với cấu hình trực tiếp qua `application.properties` thì em vẫn nêu Config Server như thành phần chuẩn kiến trúc; nhưng ý chính là “em có module centralized config + dữ liệu config dùng chung”.*)

## 4) Auth Service — Em cấu hình gì? Mục đích làm gì? Dữ liệu chạy ra sao?
### 4.1 Em cấu hình gì
Auth Service có JWT secret và expiration để tạo token.

Order Service cũng có `auth.jwt.secret` để **match** secret Auth Service.

### 4.2 Mục đích làm gì
- Chỉ user hợp lệ mới được tạo order.
- Role-based: OrderService chỉ cho `ROLE_USER`.

### 4.3 Dữ liệu chạy ra sao
- Client -> AuthService login -> nhận `accessToken` (JWT)
- Client gửi token trong header:
  - `Authorization: Bearer <token>`
- JWT chứa:
  - `userId` (subject)
  - `roles`
- OrderService decode token để lấy `userId` và kiểm quyền.

## 5) Inventory Service — Em seed Redis như thế nào? Mục đích làm gì?
### 5.1 Em seed Redis bằng gì
Trong `InventorySeedConfig`, em seed:
- `promo:stock:{promoId}:{productId}` -> là **String stock**

### 5.2 Mục đích làm gì
- Lua trong OrderService phụ thuộc vào key tồn kho.
- Nếu không seed, Lua sẽ đọc tồn kho sai -> order ra `OUT_OF_STOCK`.
- Ngoài seed tự động, InventoryService còn có API cho admin để set stock thủ công và đồng thời cập nhật luôn Redis, để bạn có thể “lập” tồn kho trước khi chạy flash-sale.
- Admin endpoints:
  - `GET /admin/promo-stocks/{promoId}/products/{productId}/stock` (ROLE_ADMIN)
  - `POST /admin/promo-stocks/{promoId}/products/{productId}/stock` (ROLE_ADMIN)
  - Body: `{"stock": 20}`

### 5.3 Dữ liệu chạy ra sao
Redis sau khi seed sẽ có ví dụ:
- `promo:stock:BF2026:1` = `20`
- `promo:stock:RETAIL:1` = `20`

## 6) Order Service — Em cấu hình gì? Mục đích làm gì?
## 6.1 Cấu hình hạ tầng trong OrderService
Trong `OrderService/src/main/resources/application.properties` em cấu hình:
- MySQL:
  - URL: `jdbc:mysql://localhost:3312/orders`
  - user: `orders`
  - password: default `orders123` (qua env)
- Redis:
  - host/port (env)
  - dùng để chạy Lua atomic và đọc remainingStock
- RabbitMQ:
  - host/port/username/password (env)
- Product service base-url:
  - `http://localhost:8086`
- Queue name:
  - `app.rabbit.order-created`
  - `app.rabbit.order-result`
- Retail promo kênh lẻ:
  - `app.order.retail-promo-id=RETAIL`

### Mục đích của các cấu hình này
- Kết nối đủ 4 hệ:
  1. DB (lưu order xác nhận)
  2. Redis (tồn kho + Lua)
  3. RabbitMQ (event confirm)
  4. Product (snapshot giá)

## 6.2 Luồng chạy đặt hàng `POST /orders`
Giả sử client đã có JWT hợp lệ.

### Bước A: Client -> API Gateway -> OrderService
1. Client gọi gateway `POST /orders/...`
2. Gateway:
   - rate limit
   - load balance tới instance OrderService bằng Eureka
3. OrderService nhận request tại `OrderController`.

### Bước B: Auth + xác định kênh promo
Trong `OrderController`:
1. `JwtAuthService.authenticate()` -> ra `userId` và `roles`.
2. `resolvePromoChannel(promoId)`:
   - `promoId` blank -> channel = `RETAIL`
   - `promoId` hợp lệ -> channel = promoId đó (vd `BF2026`)

### Bước C: Snapshot giá từ ProductService
Trong `OrderPlacementService`:
- Gọi `ProductClient.getBasePrice(productId)` -> `priceSnapshot`
- mục đích: “khóa” giá tại thời điểm đặt hàng.

### Bước D: Redis Lua atomic (điểm core)
OrderPlacementService gọi:
- `orderLuaService.tryCreateOrderDraft(promoId, userId, productId, ttlSeconds, orderDraftId)`

Lua thao tác trên các key:
- `order:dedup:{promoId}:{userId}`: chống đặt trùng
- `promo:stock:{promoId}:{productId}`: tồn kho
- `order:pending:{orderDraftId}`: draft đang chờ confirm

Kết quả Lua trả về 1 trong 3 status:
- `OK`
- `DUPLICATE`
- `OUT_OF_STOCK`

### Bước E: Nếu OK -> gửi RabbitMQ và chờ result
Nếu status = `OK`:
1. OrderService gửi `OrderCreatedMessage` vào queue:
   - `order.created`
2. OrderService chờ `OrderResultMessage` trả về từ queue:
   - `order.result`
3. `OrderAwaiter` dùng `CompletableFuture` map theo `orderDraftId`.

### Bước F: Consumer xử lý confirm -> ghi MySQL -> trả result
`OrderCreatedListener`:
1. Nhận `OrderCreatedMessage`
2. Query MySQL theo `orderDraftId`
3. Nếu chưa có:
   - tạo `OrderEntity`
   - set `status=CONFIRMED`, `confirmedAt=now`
   - `orderRepository.save(entity)`
4. Lấy `remainingStock` từ Redis
5. Gửi `OrderResultMessage` tới `order.result`

`OrderResultListener`:
- nhận message -> `orderAwaiter.complete(message)`
- trả response cho client từ `placeOrder(...)`.

## 6.7) Product Service — em cấu hình gì? để làm gì? dữ liệu chạy ra sao?
### Em cấu hình gì
- Port: `8086`
- MySQL cho dữ liệu sản phẩm: `jdbc:mysql://localhost:3310/product`
- Có seed data cho sản phẩm khi bật `app.seed.enabled` (mặc định `true`).

### Mục đích em làm gì
- Lưu thông tin sản phẩm và đặc biệt là `basePrice`.
- Cung cấp endpoint để OrderService lấy **price snapshot** tại thời điểm tạo order.

### Endpoint (Swagger) bạn test
- `POST /admin/products` (ADMIN only): tạo product mới.
- `GET /products/{id}`: lấy product theo id (OrderService gọi để lấy `basePrice`).
- `GET /products`: danh sách sản phẩm có phân trang.

### Dữ liệu chạy ra sao (liên quan Order)
- `OrderService` gọi qua `ProductClient`:
  - URL: `http://.../products/{productId}`
  - Lấy `basePrice` và lưu thành `priceSnapshot` trong draft order.

## 6.8) Inventory Service — em cấu hình gì? để làm gì?
(Bổ sung API admin bên cạnh Redis seed)

### Em cấu hình gì
- Port: `8087`
- MySQL: `jdbc:mysql://localhost:3311/inventory`
- Redis: lưu key tồn kho flash-sale theo promo & product.

### Mục đích em làm gì
- Quản lý “tồn kho theo promo” để Lua của OrderService đọc và trừ.
- Admin có thể set tồn kho trực tiếp trước khi chạy flash-sale.

### API admin (ROLE_ADMIN)
- `GET /admin/promo-stocks/{promoId}/products/{productId}/stock`
- `POST /admin/promo-stocks/{promoId}/products/{productId}/stock`
  - Body: `{"stock": 20}`
- Khi set xong, InventoryService:
  - lưu `PromoStock` vào MySQL,
  - đồng thời set Redis key tương ứng `promo:stock:{promoId}:{productId}` thành giá trị string stock.

## 6.9) Promo Service (Black Friday trigger) — em cấu hình gì? để làm gì?
### Em cấu hình gì
- Port: `8085`
- RabbitMQ queue: `app.rabbit.queue.campaign-applied` (mặc định `campaign.applied`).

### Mục đích em làm gì
- Khi admin trigger Black Friday, PromoService sẽ:
  - login vào AuthService để lấy access token (admin),
  - lấy danh sách người dùng theo role `ROLE_USER`,
  - chia chunk (mặc định `500` user/chunk),
  - gửi message “campaign applied” sang RabbitMQ để NotificationService xử lý.

### API admin (ROLE_ADMIN)
- `POST /admin/campaigns/blackfriday/{campaignId}/trigger`

### Dữ liệu chạy ra sao (Promo -> Rabbit -> Notification)
1. Controller gọi `CampaignTriggerService.triggerBlackFriday(campaignId)`.
2. `CampaignTriggerService`:
   - gọi AuthService `GET /internal/users?role=ROLE_USER`,
   - build `CampaignAppliedMessage`:
     - `campaignId`
     - `eventType="campaign.applied"`
     - `users` (list `UserRef`)
3. `CampaignPublisher` publish message vào queue `campaign.applied`.

## 6.10) Notification Service — em cấu hình gì? để làm gì?
### Em cấu hình gì
- Port: `8084`
- RabbitMQ queue nhận thông báo campaign: `campaign.applied`
- WebSocket endpoint: `/ws`

### Mục đích em làm gì
- Khi có message “campaign applied”:
  - đẩy realtime tới đúng người dùng qua WebSocket,
  - gửi email Black Friday (demo) qua MailService.

### Dữ liệu chạy ra sao
1. `CampaignAppliedListener` consume queue `campaign.applied`.
2. Với mỗi user trong message:
   - WebSocket push tới đích `/queue/campaign.applied` (theo userId)
   - gọi `mailService.sendBlackFridayMail(...)`.

## 6.11) User Service + Department Service (demo)
### Mục đích em làm gì
- Bổ sung các service demo để chứng minh hệ thống microservices chạy đồng thời, Gateway/Eureka vẫn route được.

### Endpoint chính
- UserService: `GET/POST /users/**`
- DepartmentService: `GET /departments`

## 7) Dữ liệu chạy “đi đâu” — câu trả lời ngắn gọn để nói trong lúc bảo vệ
- **JWT**: Client -> AuthService -> OrderService (đọc token để lấy userId + roles)
- **Redis**: stock + dedup/pending + Lua EVAL atomic (tránh sai tồn kho)
- **RabbitMQ**:
  - `order.created`: gửi draft sang consumer confirm
  - `order.result`: gửi trạng thái/remainingStock về OrderService để trả response
- `campaign.applied`: Promo publish để NotificationService push WebSocket + gửi mail Black Friday
- **MySQL**: lưu `OrderEntity` khi CONFIRMED
- **API Gateway**: rate limit + load balance sang instance OrderService bằng Eureka
- **Eureka**: lưu danh sách instance để Gateway/LB chọn đúng service

## 8) Cách chạy để test order (đúng theo em đã làm)
### Bước 1: Chạy Docker hạ tầng
```powershell
docker compose up -d
```

### Bước 2: Chạy các service Java
```powershell
.\run-all-services.ps1
```

### Bước 3: Test qua Swagger
1. Vào Auth Swagger: `http://localhost:8083/swagger-ui.html`
   - login `user@example.com` / `user123`
2. Vào Order Swagger: `http://localhost:8088/swagger-ui.html`
   - bấm `Authorize` với `Bearer <accessToken>`
3. Gọi `POST /orders` với body mua lẻ:
```json
{
  "productId": 1,
  "quantity": 1,
  "promoId": ""
}
```
Kết quả sẽ được xác nhận qua RabbitMQ và lưu MySQL.

### (Optional) Test Flash-sale + Black Friday notification
1. Vào Auth Swagger và login bằng admin `admin@example.com` / `admin123` để lấy token (ROLE_ADMIN).
2. Inventory Swagger (8087):
   - mở endpoint `POST /admin/promo-stocks/{promoId}/products/{productId}/stock`
   - set stock (nếu bạn muốn chắc chắn trước khi trigger).
3. Promo Swagger (8085):
   - gọi `POST /admin/campaigns/blackfriday/BF2026/trigger` (bấm `Authorize` với token admin).
   - Promo sẽ chia chunk user và publish message `campaign.applied` vào RabbitMQ.
4. Notification:
   - chạy NotificationService (8084).
   - mở WebSocket `/ws` và lắng nghe đích WebSocket `/queue/campaign.applied` cho userId của bạn.
   - Khi message về, NotificationService sẽ vừa push realtime vừa gửi mail Black Friday.

## 9) Câu kết nhấn mạnh đúng kỹ thuật
Em nhấn mạnh điểm “đắt giá” của dự án:
- **Lua atomic trên Redis** đảm bảo đúng tồn kho khi có nhiều request đồng thời và chống đặt trùng.
- **RabbitMQ** giúp tách bước confirm DB, giảm thời gian API và tăng khả năng chịu tải.
- **API Gateway + Eureka** cung cấp định tuyến linh hoạt và load balancing theo service name.

Trong dự án của bạn, Eureka đóng vai trò “Service Registry / Service Discovery”.

Nó dùng để làm gì?
Khi mỗi microservice (order-service, promo-service, product-service, …) khởi động, nó sẽ đăng ký với Eureka bằng cấu hình trong application.properties (ví dụ eureka.client.service-url.defaultZone=...). Dự án cũng có dependency spring-cloud-starter-netflix-eureka-client trong pom.xml để làm việc đó.

Mục đích ra sao?
Eureka sẽ lưu bản đồ: service-name → danh sách instance host:port đang chạy. Nhờ đó API Gateway có thể route/load-balance theo tên service (dùng cơ chế lb("order-service"), lb("promo-service"), …) thay vì phải hard-code địa chỉ IP/port trong code.

=> Tóm lại: Eureka giúp hệ thống tự tìm ra service đang chạy ở đâu, hỗ trợ load balancing khi scale nhiều instance, và giảm phụ thuộc vào địa chỉ cố định
