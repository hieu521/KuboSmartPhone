package com.example.inventory.config;

import com.example.inventory.model.PromoStock;
import com.example.inventory.repo.PromoStockRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class InventorySeedConfig {

    @Bean
    CommandLineRunner inventorySeedRunner(
            PromoStockRepository promoStockRepository,
            StringRedisTemplate redisTemplate,
            @Value("${app.seed.enabled:true}") boolean seedEnabled,
            @Value("${app.seed.promo-id:BF2026}") String promoId,
            @Value("${app.seed.retail-promo-id:RETAIL}") String retailPromoId
    ) {
        return args -> {
            if (!seedEnabled) {
                return;
            }

            seedStock(promoStockRepository, redisTemplate, promoId, 1L, 20L);
            seedStock(promoStockRepository, redisTemplate, promoId, 2L, 15L);
            seedStock(promoStockRepository, redisTemplate, promoId, 3L, 10L);

            // Kênh mua thường (OrderService POST /orders) — cùng mức tồn demo
            seedStock(promoStockRepository, redisTemplate, retailPromoId, 1L, 20L);
            seedStock(promoStockRepository, redisTemplate, retailPromoId, 2L, 15L);
            seedStock(promoStockRepository, redisTemplate, retailPromoId, 3L, 10L);
        };
    }

    private static void seedStock(
            PromoStockRepository promoStockRepository,
            StringRedisTemplate redisTemplate,
            String promoId,
            Long productId,
            Long stock
    ) {
        PromoStock entity = promoStockRepository.findByPromoIdAndProductId(promoId, productId)
                .orElseGet(() -> {
                    PromoStock ps = new PromoStock();
                    ps.setPromoId(promoId);
                    ps.setProductId(productId);
                    return ps;
                });
        entity.setStock(stock);
        promoStockRepository.save(entity);

        redisTemplate.opsForValue().set(stockKey(promoId, productId), String.valueOf(stock));
    }

    private static String stockKey(String promoId, Long productId) {
        return "promo:stock:" + promoId + ":" + productId;
    }
}
