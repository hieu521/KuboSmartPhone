package com.example.order.mq;

import java.math.BigDecimal;
import java.util.List;

public class OrderCreatedMessage {
    private String orderDraftId;
    private String promoId;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private BigDecimal priceSnapshot;
    private List<OrderCartItemMessage> items;

    public String getOrderDraftId() {
        return orderDraftId;
    }

    public void setOrderDraftId(String orderDraftId) {
        this.orderDraftId = orderDraftId;
    }

    public String getPromoId() {
        return promoId;
    }

    public void setPromoId(String promoId) {
        this.promoId = promoId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

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

    public List<OrderCartItemMessage> getItems() {
        return items;
    }

    public void setItems(List<OrderCartItemMessage> items) {
        this.items = items;
    }
}

