package com.example.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public class OrderCartItemResponse {

    @Schema(description = "Product id")
    private Long productId;

    @Schema(description = "Quantity")
    private Integer quantity;

    @Schema(description = "Price snapshot per unit")
    private BigDecimal priceSnapshot;

    @Schema(description = "Remaining stock after reservation")
    private Long remainingStock;

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

    public BigDecimal getPriceSnapshot() {
        return priceSnapshot;
    }

    public void setPriceSnapshot(BigDecimal priceSnapshot) {
        this.priceSnapshot = priceSnapshot;
    }

    public Long getRemainingStock() {
        return remainingStock;
    }

    public void setRemainingStock(Long remainingStock) {
        this.remainingStock = remainingStock;
    }
}

