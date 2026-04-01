package com.example.order.mapping;

import org.mapstruct.Context;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Logic map không chỉ là “copy field”: merge null-coalescing, fallback từ Lua/context.
 * Được MapStruct gọi qua {@link Named} + {@link Context} — interface mapper vẫn gọn.
 */
@Component
public class OrderMappingHelper {

    @Named("remainingOrFallback")
    public Long remainingOrFallback(Long fromMessage, @Context OrderMappingFallback fallback) {
        return fromMessage != null ? fromMessage : fallback.remainingStock();
    }

    @Named("priceOrFallback")
    public BigDecimal priceOrFallback(BigDecimal fromMessage, @Context OrderMappingFallback fallback) {
        return fromMessage != null ? fromMessage : fallback.priceSnapshot();
    }
}
