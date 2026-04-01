# Test services với OpenAPI/Swagger

## 1. Chạy hạ tầng (Docker)

```powershell
docker compose up -d
```

Đảm bảo các container: redis, rabbitmq, mysql-auth, mysql-product, mysql-inventory, mysql-order (và notification-service nếu cần).

## 2. Chạy các service Java

```powershell
.\run-all-services.ps1
```

Hoặc chạy từng service thủ công (trong các terminal riêng):

```powershell
cd AuthService && mvn spring-boot:run
cd ProductService && mvn spring-boot:run
cd InventoryService && mvn spring-boot:run
cd OrderService && mvn spring-boot:run
cd PromoService && mvn spring-boot:run
```

## 3. Swagger UI – test API

| Service      | URL                                      |
|-------------|-------------------------------------------|
| Auth        | http://localhost:8083/swagger-ui.html     |
| Product     | http://localhost:8086/swagger-ui.html     |
| Inventory   | http://localhost:8087/swagger-ui.html     |
| Order       | http://localhost:8088/swagger-ui.html     |
| Promo       | http://localhost:8085/swagger-ui.html     |
| Notification| http://localhost:8084/swagger-ui.html     |

## 4. Seed data

| Loại     | Giá trị |
|----------|---------|
| Admin    | admin@example.com / admin123 |
| User     | user@example.com / user123   |
| Promo ID | BF2026 |
| Products | 3 sản phẩm (ID 1, 2, 3)     |

## 5. Thứ tự test nhanh

1. **Auth** → Login `user@example.com` / `user123` → Copy `accessToken`
2. **Product** → GET `/products` (không cần token)
3. **Inventory** → Login admin → POST set stock cho promo `BF2026`, product 1 (response có `redisKey`) hoặc GET `/admin/promo-stocks/BF2026/products/1/stock`
4. **Order** → **Một endpoint** `POST /orders` + token user:
   - **Mua lẻ**: body `{"productId": 1, "quantity": 1}` (không gửi `promoId`) → kênh **`RETAIL`**.
   - **Flash sale / có chiến dịch**: thêm `"promoId": "BF2026"` trong JSON (không nhét `{promoId}` placeholder).
   - OrderService gọi ProductService (8086) lấy giá; nếu Product tắt sẽ lỗi **502**.

## 6. Swagger: `Authorization` vs `X-Authorization`

Với các API có `@SecurityRequirement(name = "bearerAuth")` (Product, Inventory, Order, Promo):

- **Nút Authorize**: Swagger gửi header `Authorization: Bearer <token>` — nên dùng cách này trước.
- Nếu ô `Authorization` trên từng operation không gửi đúng: dùng thêm header **`X-Authorization`** với giá trị `Bearer <token>` (hoặc token thuần nếu service hỗ trợ).

Backend ưu tiên `Authorization`, nếu trống thì lấy `X-Authorization`.

Endpoint nội bộ **Auth** `GET /internal/users` cũng hỗ trợ cùng quy tắc.
