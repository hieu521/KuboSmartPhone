# MapStruct + Spring: giữ mapper gọn, logic phức tạp ở Helper

## Khi nào dùng

- **Chỉ MapStruct**: map field đổi tên / kiểu đơn giản (`id` → `userId`, record → DTO).
- **Thêm `@Component` Helper** khi cần:
  - merge null / fallback (`OrderResultMessage` + context từ Lua),
  - format / normalize (`trim` tên sản phẩm),
  - quy ước ngoài DB (Redis key từ `promoId` + `productId`),
  - gọi repository / service (inject bean vào Helper, expose method `@Named` hoặc gọi từ Helper trước/sau map).

## Pattern trong repo

1. **`@Mapper(componentModel = "spring", uses = XxxHelper.class)`**  
   Spring inject implementation của mapper + bean helper.

2. **Helper là class `@Component`**, method annotate **`@org.mapstruct.Named("...")`**:
   - MapStruct gọi qua **`qualifiedByName`** trên `@Mapping`.
   - Tham số bổ sung: **`@Context`** (ví dụ `OrderMappingFallback`) — không pollute signature API công khai nếu chỉ dùng nội bộ map.

3. **Record / object `@Context`**  
   Gom fallback (remaining stock, price snapshot) thay vì thêm 5 tham số vào mapper.

4. **Interface mapper**  
   Chỉ khai báo `@Mapping`; logic if/else, DB, Redis nằm Helper hoặc service.

5. **Expression cần bean inject**  
   MapStruct không tự thêm field helper khi chỉ dùng `expression` trên **interface**. Khi đó dùng **`abstract class` mapper** + `@Autowired protected XxxHelper helper` rồi `expression = "java(helper.method(entity))"`.

## Ví dụ có trong code

| Service   | Helper                 | Mapper              |
|----------|------------------------|---------------------|
| Order    | `OrderMappingHelper`   | `OrderResultMapper` |
| Product  | `ProductMappingHelper` | `ProductDraftMapper`|
| Promo    | `PromoMappingHelper`  | `UserRefMapper`     |
| Inventory| `PromoStockMappingHelper` | `PromoStockMapper`|

## Không bắt buộc mọi module

Chỉ thêm Helper khi map vượt quá copy field. Service không có JPA/DTO (ví dụ chỉ nhận message) có thể không cần MapStruct.
