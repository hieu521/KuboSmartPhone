package com.example.inventory.mapping;

import com.example.inventory.model.PromoStock;
import org.springframework.stereotype.Component;

/**
 * Khóa Redis / quy ước key — không thuộc ánh xạ field 1-1 từ JPA.
 */
@Component
public class PromoStockMappingHelper {

    public static String redisStockKey(String promoId, Long productId) {
        return "promo:stock:" + promoId + ":" + productId;
    }

    public String redisKeyFromEntity(PromoStock entity) {
        if (entity == null || entity.getPromoId() == null || entity.getProductId() == null) {
            return null;
        }
        return redisStockKey(entity.getPromoId(), entity.getProductId());
    }
}
