# Kịch bản thuyết trình (đã gộp vào file chi tiết)

File chính để đọc/thuyết trình là: `THUYET_TRINH_DU_AN_CHI_TIET.md`.

## 1) Giới thiệu nhanh kiến trúc
Đây là dự án e-commerce microservices cho sản phẩm điện thoại, chạy theo kiến trúc nhiều service và có đầy đủ các thành phần: **Service Registry (Eureka)**, **API Gateway**, **Centralized Configuration (Config Server)**, **Auth Service**, và luồng **Order** dùng **Redis + Lua (atomic)** kết hợp **RabbitMQ** để confirm đơn và **MySQL** để lưu trạng thái.

Trong dự án, em đảm bảo các yêu cầu kỹ thuật:
- **Redis**: lưu stock theo promo và thực hiện logic atomic bằng Lua để chống đặt trùng và trừ tồn an toàn.
- **RabbitMQ**: phát sự kiện `order.created` và đợi kết quả `order.result`.
- **MySQL**: lưu thực thể order sau khi xác nhận.
- **Spring Cloud**: dùng Eureka để discovery và Gateway để định tuyến.

## 2) Service Registry (Eureka)
### Em đã cấu hình như thế nào?
Mỗi service (Auth, Product, Inventory, Order, Promo, …) đều có cấu hình `eureka.client.service-url.defaultZone` để trỏ tới Eureka server.

### Chạy và dữ liệu chạy ra sao?
Khi service khởi động:
1. Service gửi request đăng ký với Eureka.
2. Eureka lưu lại danh sách instance theo dạng: `service-name -> list (host:port)`.

Khi Gateway cần gọi tới một service, Gateway dựa vào mapping trong Eureka để biết service đó đang chạy ở những đâu.

## 3) API Gateway
### Em đã làm gì trong Gateway?
Gateway có route cho từng service và có 2 phần quan trọng:
1. **Rate limiting** bằng Bucket4j để hạn chế request (giới hạn theo IP).
2. **Load balancing** để phân phối request tới đúng instance của service đích.

Trong code, route xử lý `POST /orders/**` dùng filter:
- `.filter(lb("order-service"))`

### Luồng request chạy như thế nào?
Client gọi vào Gateway (port 8080) theo endpoint mong muốn.
Gateway sẽ:
1. áp rate limit,
2. lấy danh sách instance của `order-service` từ Eureka,
3. chọn instance phù hợp (load balancer policy),
4. forward request tới instance đó.

## 4) Centralized configuration (Config Server)
### Em đã cấu hình như thế nào?
Config Server chạy trên port `8888` và dùng profile `native` để đọc file cấu hình từ `classpath:/config`.

Em có `config/global.properties` để chứa các cấu hình dùng chung.

### Chạy và dữ liệu chạy như thế nào?
Khi service sử dụng centralized config:
- service sẽ lấy các giá trị cấu hình từ Config Server,
- dùng để tránh lặp cấu hình giữa các service.

## 5) Auth Service (JWT)
### Luồng chạy
1. Client gọi endpoint login trong Auth Service để lấy **access token JWT**.
2. Token được gửi kèm cho các request nghiệp vụ thông qua header `Authorization: Bearer <token>`.

### Dữ liệu token
JWT chứa `subject` (userId) và claim `roles`.
Order Service đọc token và kiểm tra role để đảm bảo user có quyền đặt hàng.

## 6) Luồng đặt hàng (Order Service) chi tiết
Em mô tả luồng theo đúng luồng kỹ thuật trong code: **API Gateway -> OrderService -> Redis Lua -> RabbitMQ -> MySQL -> trả response**.

### 6.1 Client gọi
Endpoint chính: **`POST /orders`**.

Ví dụ body mua lẻ (promoId để trống hoặc bỏ không gửi):
```json
{
  "productId": 1,
  "quantity": 1,
  "promoId": ""
}
```
Order Service sẽ map `promoId` rỗng sang kênh **`RETAIL`**.

### 6.2 OrderService xác thực và chọn kênh promo
Trong `OrderController`:
1. `JwtAuthService.authenticate()` decode JWT để lấy `userId` và `roles`.
2. `resolvePromoChannel(promoId)`:
   - nếu `promoId` blank thì channel = `RETAIL`,
   - nếu `promoId` có giá trị hợp lệ thì dùng theo promo đó.

### 6.3 OrderService lấy giá snapshot từ ProductService
Trong `OrderPlacementService`:
- em gọi `ProductClient.getBasePrice(productId)` để lấy `priceSnapshot`.
Lý do: giữ “giá tại thời điểm đặt hàng” để không bị thay đổi bởi giá cập nhật sau đó.

### 6.4 Redis Lua atomic: trừ stock và chống đặt trùng
Đây là điểm “core” của flash-sale.

OrderService gọi `OrderLuaService.tryCreateOrderDraft(...)`, chạy Lua script qua Redis.
Lua sẽ đọc và thao tác trên các key:
- `order:dedup:{promoId}:{userId}`: chống đặt trùng trong cửa sổ flash-sale.
- `promo:stock:{promoId}:{productId}`: giá trị tồn kho hiện tại.
- `order:pending:{orderDraftId}`: đánh dấu draft order đang chờ xác nhận.

Lua trả về `status` gồm:
- `OK`
- `DUPLICATE`
- `OUT_OF_STOCK`

Nếu `OK` thì hệ thống tạo draft order và chuyển sang bước gửi message.

### 6.5 RabbitMQ: tạo sự kiện và chờ confirm
Khi Lua trả `OK`:
1. OrderService gửi message `order.created` thông qua `RabbitTemplate`.
2. Đồng thời OrderService chờ kết quả trên hàng đợi `order.result` bằng cơ chế `OrderAwaiter` (dùng `CompletableFuture`).

Các message/data chính:
- `OrderCreatedMessage`: gồm `orderDraftId`, `promoId`, `userId`, `productId`, `quantity`, `priceSnapshot`.
- `OrderResultMessage`: gồm `orderDraftId`, `orderId`, `status`, `remainingStock`, `message`, `priceSnapshot`.

Tên queue (mặc định trong config):
- `app.rabbit.order-created` = `order.created`
- `app.rabbit.order-result` = `order.result`

### 6.6 Consumer xử lý confirm và ghi MySQL
`OrderCreatedListener` là consumer cho queue `order.created`.
Khi nhận message:
1. Tìm order trong MySQL theo `orderDraftId`.
2. Nếu chưa có thì tạo mới `OrderEntity` và set:
   - `status = CONFIRMED`
   - `confirmedAt = now`
3. Lưu vào MySQL bằng `orderRepository.save(entity)`.
4. Lấy lại `remainingStock` từ Redis để trả response chính xác.
5. Gửi `OrderResultMessage` tới queue `order.result`.

Sau đó `OrderResultListener` nhận `order.result` và gọi `orderAwaiter.complete(message)` để hoàn tất `placeOrder` và trả response cho client.

## 7) Dữ liệu “đi đâu” trong hệ thống (tóm tắt để nói miệng)
- JWT: đi từ Client -> Auth Service (tạo token) và từ Client -> Order Service (gọi API).
- Redis: lưu stock flash-sale, kiểm tra trừ kho bằng Lua, lưu dedup/pending key.
- RabbitMQ:
  - `order.created`: mang draft order tới consumer confirm.
  - `order.result`: mang trạng thái confirm về để trả response.
- MySQL: lưu `OrderEntity` khi confirm thành công.
- API Gateway: nhận request từ client, apply rate limit, LB theo `lb("order-service")`.
- Eureka: lưu danh sách instance để Gateway/LB biết service nào đang chạy.

## 8) Cách chạy để test nhanh
### Bước 1: Chạy Docker hạ tầng
Chạy từ thư mục gốc:
```powershell
docker compose up -d
```

### Bước 2: Chạy các service Java
```powershell
.\run-all-services.ps1
```

### Bước 3: Test bằng Swagger
- Auth: http://localhost:8083/swagger-ui.html
- Product: http://localhost:8086/swagger-ui.html
- Inventory: http://localhost:8087/swagger-ui.html
- Order: http://localhost:8088/swagger-ui.html
- Promo: http://localhost:8085/swagger-ui.html

Test order:
1. Vào Auth Swagger login để lấy `accessToken`.
2. Sang Order Swagger bấm `Authorize` với `Bearer <accessToken>`.
3. Gọi `POST /orders` với body mua lẻ hoặc body có `promoId` hợp lệ.

## 9) Gợi ý câu kết khi thuyết trình
Em nhấn mạnh rằng phần flash-sale được đảm bảo atomic bằng Redis Lua để tránh trừ kho sai, còn việc ghi MySQL được thực hiện bất đồng bộ qua RabbitMQ để tách bước “tạo draft” và “confirm lưu DB”. Tất cả được định tuyến qua API Gateway với rate limiting và load balancing dựa trên Eureka.

