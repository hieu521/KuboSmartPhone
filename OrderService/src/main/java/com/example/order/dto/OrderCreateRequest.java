package com.example.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class OrderCreateRequest {
    @NotNull
    private Long productId;

    @Min(1)
    private Integer quantity = 1;

    /**
     * Mã kênh khuyến mãi (vd. BF2026). Để trống = mua lẻ, hệ thống dùng kênh RETAIL (cấu hình server).
     */
    @Schema(description = "Optional campaign/stock channel id (e.g. BF2026). Omit for normal retail checkout.")
    private String promoId;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getPromoId() {
        return promoId;
    }

    public void setPromoId(String promoId) {
        this.promoId = promoId;
    }
}

