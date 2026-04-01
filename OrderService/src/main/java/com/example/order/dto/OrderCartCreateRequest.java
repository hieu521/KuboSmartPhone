package com.example.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class OrderCartCreateRequest {

    @NotEmpty
    @Valid
    @Schema(description = "Cart items (multi product)")
    private List<OrderCartItemRequest> items;

    /**
     * Mã kênh khuyến mãi (vd. BF2026). Để trống = mua lẻ, hệ thống dùng kênh RETAIL (cấu hình server).
     */
    @Schema(description = "Optional campaign/stock channel id (e.g. BF2026). Omit for normal retail checkout.")
    private String promoId;

    public List<OrderCartItemRequest> getItems() {
        return items;
    }

    public void setItems(List<OrderCartItemRequest> items) {
        this.items = items;
    }

    public String getPromoId() {
        return promoId;
    }

    public void setPromoId(String promoId) {
        this.promoId = promoId;
    }
}

