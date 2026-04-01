package com.example.order.mapping;

import java.math.BigDecimal;

/**
 * Dữ liệu phụ truyền qua {@link org.mapstruct.Context} khi map {@link com.example.order.mq.OrderResultMessage}
 * sang {@link com.example.order.dto.OrderCreateResponse} (fallback khi message thiếu stock/price).
 */
public record OrderMappingFallback(long remainingStock, BigDecimal priceSnapshot) {}
