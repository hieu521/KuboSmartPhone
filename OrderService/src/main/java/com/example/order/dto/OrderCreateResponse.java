package com.example.order.dto;

import com.example.order.domain.OrderStatus;

import java.math.BigDecimal;

public class OrderCreateResponse {
    private String orderDraftId;
    private Long orderId;
    private OrderStatus status;
    private Long remainingStock;
    private String message;
    private BigDecimal priceSnapshot;

    public OrderCreateResponse() {
    }

    public String getOrderDraftId() {
        return orderDraftId;
    }

    public void setOrderDraftId(String orderDraftId) {
        this.orderDraftId = orderDraftId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Long getRemainingStock() {
        return remainingStock;
    }

    public void setRemainingStock(Long remainingStock) {
        this.remainingStock = remainingStock;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public BigDecimal getPriceSnapshot() {
        return priceSnapshot;
    }

    public void setPriceSnapshot(BigDecimal priceSnapshot) {
        this.priceSnapshot = priceSnapshot;
    }
}

