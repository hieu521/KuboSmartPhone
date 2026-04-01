# Thiết kế hệ thống thương mại điện tử bán điện thoại (Microservices)

## 0) Mục tiêu & yêu cầu bài toán
- Thiết kế theo mô hình microservice với đầy đủ:
  - Service Registry: Eureka
  - API Gateway: Spring Cloud Gateway
  - Centralized Configuration: Spring Cloud Config Server
  - Auth service: tách riêng
  - Load balancing: dùng `lb://` (Gateway) + client-side LB (nếu cần)
  - Message Broker: RabbitMQ
  - Redis: xử lý flash-sale (high concurrency) + dedup + caching + realtime mapping
- Yêu cầu xử lý tải cao:
  - Ngày khuyến mại: rất nhiều người cùng đặt hàng => dùng Redis + atomic (Lua) + MQ để giảm nghẽn DB
  - Black Friday: notify cho hàng triệu user => dùng RabbitMQ fan-out theo chunk + retry/DLQ

## 1) Các microservice đề xuất
1. `eureka-service` (Service Registry)
2. `api-gateway` (API Gateway)
3. `config-server` (Centralized Configuration)
4. `auth-service` (Login/Register + JWT + role/permission)
5. `product-service` (Danh mục/sản phẩm/giá cơ bản)
6. `promo-service` (Quản lý promo/flash-sale + trigger chiến dịch Black Friday)
7. `inventory-service` (Tồn kho flash-sale theo promo, hoặc đóng vai trò quản lý tồn)
8. `order-service` (Tạo order, trạng thái: PENDING/CONFIRMED/FAILED)
9. `notification-service` (Gửi mail + realtime WebSocket cho user đang online)

> (Tuỳ chọn) `user-service`:
> - Hồ sơ user, email, segment dùng cho Black Friday, prefer ngôn ngữ.

### Repository của bạn (mapping ý tưởng)
- Bạn đã có `EurekaService`, `APIGateway`, `ConfigServer`, `UserService`, `DepartmentService`.
- Ở đồ án, `UserService` thường đóng vai trò `user-service`.
- Các service “promo/order/notification/auth” có thể được phát triển thêm theo đúng luồng bên dưới.

## 2) Role & phân quyền (RBAC)
Bạn chốt theo hướng:
- `ROLE_USER`: đặt hàng, xem thông tin, nhận notify
- `ROLE_ADMIN`: tạo promo, kích hoạt Black Friday, quản lý hệ thống

Có thể thêm:
- `ROLE_MARKETING` (tuỳ chọn): tạo campaign/trigger notify nhưng không can thiệp cấu hình hệ thống

### Enum gợi ý trong hệ thống
- `Role` (RBAC):
  - `ROLE_USER`
  - `ROLE_ADMIN`
  - (tuỳ chọn) `ROLE_MARKETING`
- `OrderStatus` (order-service):
  - `PENDING` (chờ DB xác nhận / chờ xử lý async)
  - `CONFIRMED` (DB đã ghi thành công)
  - `FAILED` (xử lý thất bại)
- `CampaignStatus` (promo-service):
  - `DRAFT` (chưa kích hoạt)
  - `ACTIVE` (đang chạy flash-sale/trigger)
  - `ENDED` (kết thúc)
- `NotificationStatus` (notification-service):
  - `SENT`/`DELIVERED`
  - `FAILED`
  - (tuỳ chọn) `RETRYING`

### Claim trong JWT
Token JWT nên chứa:
- `sub`: userId
- `roles`: `["ROLE_USER", "ROLE_ADMIN"]`

### Nơi enforce role
- Tốt nhất:
  - Gateway chặn route quan trọng trước (giảm tải)
  - Service enforce ở endpoint nhạy cảm:
    - Admin endpoints: tạo promo / trigger Black Friday
    - User endpoints: đặt hàng flash-sale

## 3) Lựa chọn triển khai theo bài toán bạn chốt
Bạn chọn:
1. Flash-sale: **B** => *chờ DB xác nhận rồi mới trả response*.
2. Black Friday notify: **B** => *Mail + WebSocket/push cho user online*.
3. Auth: **tách AuthService riêng**.

## 3.1) AuthService: AccessToken + RefreshToken (có refresh)
### Token types
- **accessToken (JWT)**: thời hạn ngắn (ví dụ 5–15 phút), dùng để gọi API.
- **refreshToken**: thời hạn dài hơn (ví dụ 7–30 ngày), dùng để cấp accessToken mới khi hết hạn.

### Claim trong accessToken
- `sub`: userId
- `roles`: ví dụ `["ROLE_USER","ROLE_ADMIN"]`
- `jti`: id duy nhất của token (dùng để revoke/dedup nếu cần)

### Endpoint
- `POST /auth/login`  
  Trả về: `{ accessToken, refreshToken }`
- `POST /auth/refresh`  
  Nhận: `{ refreshToken }`  
  Trả về: `{ accessToken }` (và có thể trả luôn refreshToken mới)
- `POST /auth/logout` (tuỳ chọn)  
  Nhận: `{ refreshToken }`  
  Thu hồi refresh token đó.

### Luồng dùng token
1. Client gửi `Authorization: Bearer <accessToken>` tới API Gateway.
2. Nếu `accessToken` hết hạn:
   - Client gọi `POST /auth/refresh` bằng `refreshToken`
   - AuthService validate refresh token và cấp `accessToken` mới.

### Redis cho revoke/rotation refreshToken (gợi ý)
- Nếu dùng **blacklist refresh token**:
  - Khi logout hoặc phát hiện token bị lộ: lưu key vào Redis:
    - `auth:refresh:blacklist:{jti} = 1` với TTL = thời hạn còn lại của refresh token
  - Khi `/auth/refresh`:
    - check `auth:refresh:blacklist:{jti}` => nếu có thì từ chối cấp mới.

- Nếu dùng **refresh token rotation** (khuyến nghị):
  - Mỗi lần refresh cấp **refreshToken mới** và vô hiệu refreshToken cũ:
    - Lưu trạng thái token hiện hành theo user/tokenId:
      - `auth:refresh:current:{userId} = <refreshTokenHashOrId>`
  - Khi nhận refresh token:
    - so khớp với giá trị hiện hành trong Redis để chống replay.

## 4) Thiết kế luồng Flash-sale (high concurrency)
### 4.1 Endpoint đề xuất
- `POST /orders` (một endpoint thống nhất)
  - Body: `productId`, `quantity`; **`promoId` tùy chọn** — có thì dùng kênh tồn flash sale (vd. `BF2026`), không có thì dùng kênh bán lẻ cấu hình (`RETAIL`).
  - Auth: cần `ROLE_USER` (hoặc claim tương ứng)
  - Response (theo lựa chọn B): chỉ trả khi DB xác nhận

### 4.2 Luồng xử lý (sequence)
1. Client gọi qua `api-gateway` tới `order-service` (hoặc `promo-service` đóng vai trò facade).
2. `auth-service` cấp/validate JWT; gateway/service xác nhận role hợp lệ.
3. `order-service` thực thi luồng nhanh bằng Redis:
   - `order-service` gọi Lua script atomic (trong Redis):
     - Kiểm tra promo stock còn không
     - Kiểm tra idempotency (user có đặt promo này chưa)
     - Nếu hợp lệ:
       - Trừ stock
       - Ghi idempotency => `orderDraftId` (hoặc mã order tạm)
       - Ghi trạng thái tạm `order:pending:{orderDraftId}`
4. Redis ok => `order-service` publish RabbitMQ message:
   - Queue: `order.created`
   - Payload: `{ orderDraftId, promoId, userId, productId, priceSnapshot, createdAt }`
5. `order-service` **chờ DB xác nhận**:
   Có 2 cách (khuyến nghị cho demo):
   - Cách A (demo đơn giản, dễ hiểu): RPC-style hoặc chờ consumer trả về qua reply queue `order.result.{orderDraftId}`
   - Cách B (production chuẩn hơn): trả `202 Accepted` rồi polling/websocket; tuy nhiên bạn đã chọn B (đợi DB), nên ưu tiên A/RPC cho phù hợp đồ án.
6. `order-service` consumer nhận event `order.created`:
   - Persist order vào DB với trạng thái `CONFIRMED`
   - Nếu DB fail => status `FAILED` và publish `order.failed` (tuỳ policy có hoàn stock hay không)
7. Khi consumer DB confirm => publish kết quả cho request đang chờ (reply queue).
8. HTTP request trả response final:
   - `200/201` + `orderId`, `status=CONFIRMED`
   - hoặc `409`/`400` + lý do: hết hàng / đã đặt

### 4.3 Redis keys & atomic (gợi ý)
Redis keys:
- `promo:stock:{promoId}:{productId}` => số lượng còn
- `order:dedup:{promoId}:{userId}` => trả về `orderDraftId` hoặc flag đặt
- `order:pending:{orderDraftId}` => metadata tạm (tuỳ chọn)

Pseudo logic Lua (mô tả):
- Input: promoId, productId, userId, requestedQty(=1), orderDraftId
- If `order:dedup` đã tồn tại => return `DUPLICATE`
- Else:
  - If stock <= 0 => return `OUT_OF_STOCK`
  - Else:
    - DECR stock
    - SET `order:dedup` => orderDraftId (with TTL)
    - SET `order:pending` => pending
    - return `OK`

## 5) Thiết kế Black Friday notify (Mail + WebSocket)
### 5.1 Endpoint/trigger admin
- `POST /admin/campaigns/blackfriday/{campaignId}/trigger`
  - Auth: role `ROLE_ADMIN`

### 5.2 Luồng xử lý (sequence)
1. `promo-service` kích hoạt campaign Black Friday:
   - Publish message lên RabbitMQ: `promo.notify.blackfriday`
2. `notification-service` nhận message trigger:
   - Xác định tập user mục tiêu (từ DB hoặc Redis segment)
   - Chia thành chunk/batch (vd: 1000 user/chunk)
3. Producer tạo các message dạng chunk:
   - Queue: `promo.notify.blackfriday`
   - Payload chunk: `{ campaignId, chunkId, userIds[] }`
4. Consumer `notification-service` xử lý từng chunk:
   - Với mỗi `userId`:
     - Render template email
     - Gửi mail (có retry, backoff)
     - Nếu user online:
       - Push qua WebSocket (hoặc queue nội bộ để WS gateway gửi)
   - Ghi log trạng thái:
     - `delivered` / `failed`
     - cập nhật tiến độ campaign/chunk
5. Retry & DLQ:
   - Lỗi tạm thời (timeout provider): retry N lần
   - Lỗi vĩnh viễn: đưa vào DLQ `promo.notify.blackfriday.dlq`

### 5.3 Redis hỗ trợ cho notify
- Dedup:
  - `notify:dedup:{campaignId}:{userId}` => tránh gửi trùng
- Progress:
  - `notify:progress:{campaignId}` => số chunk done/total
- Online mapping:
  - `ws:online:{userId} => sessionId(s)` (tuỳ cách giữ session)

## 6) WebSocket sử dụng thế nào cho đúng
WebSocket không nên dùng để “bắn 1 thông báo cho triệu người”.
Nó nên dùng cho các sự kiện real-time theo user:
- Order status: CONFIRMED/FAILED
- Promo status: user online nhận ngay khi campaign áp dụng

### Luồng realtime
- `order-service` publish `order.confirmed` event lên RabbitMQ
- `notification-service` (hoặc realtime-gateway) nhận event:
  - Tra Redis xem user có online không
  - Nếu online => gửi qua WebSocket tới sessionId

## 7) Centralized configuration (Config Server)
### 7.1 File dùng chung
- Tạo file trong `src/main/resources/config/global.properties`
- Ví dụ nội dung:
  - `jwt.secret=...`
  - `rabbitmq.host=...`
  - `redis.host=...`
  - logging level, feature flags

### 7.2 Client config import
Trong mỗi service:
- Thêm `spring.config.import=optional:configserver:http://{config-server-host}:8888`
- `spring.cloud.config.name={service-name}` và có thể import thêm `global`:
  - Dùng `spring.cloud.config.name=global,{service-name}` tuỳ cách bạn triển khai

> Bạn có thể làm “theo đúng bài” bằng native mode (classpath) như bạn đang làm.

## 8) Load balancing
- API Gateway route dùng `lb://{service-name}`
- Eureka giúp mapping service name -> instance
- Khi gọi service-to-service:
  - Dùng Spring Cloud LoadBalancer
  - (tuỳ chọn) thêm circuit breaker/rate limiting

## 9) Checklist để bạn thuyết trình đồ án
- Có sơ đồ microservice + cơ chế service discovery (Eureka)
- Có API Gateway routes + `lb://`
- Có Config Server + file `global.properties`
- Auth:
  - AccessToken + RefreshToken (có refresh)
  - JWT + role claims (roles trong accessToken)
  - Enforce ở gateway + service nhạy cảm
- Flash-sale:
  - Redis stock + Lua atomic
  - MQ event `order.created`
  - Lựa chọn B: chờ DB confirm trước trả response
- Black Friday:
  - RabbitMQ chunk messages
  - Retry + DLQ
  - Mail chính, WebSocket chỉ realtime cho user online

## 10) Các câu hỏi để “duyệt” bản thiết kế
Bạn xác nhận giúp mình 1 dòng:
1. Flash-sale (B) bạn muốn response trả về gồm những field nào: `orderId`, `status`, `remainingStock`, `message`? trả về đầy đủ đi 
2. Notify Black Friday dùng mail template theo ngôn ngữ (vi/en) hay 1 template? cả hai và có template đẹp tí 
3. WebSocket bạn muốn “chỉ đẩy event order” hay thêm event “campaign applied”? thêm event campaign applied

