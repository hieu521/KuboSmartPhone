package com.example.order.mq;

import com.example.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public class OrderResultMessage {
    private String orderDraftId;
    private Long orderId;
    private OrderStatus status;
    private Long remainingStock;
    private String message;
    private BigDecimal priceSnapshot;
    private List<OrderCartItemResultMessage> items;

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

    public List<OrderCartItemResultMessage> getItems() {
        return items;
    }

    public void setItems(List<OrderCartItemResultMessage> items) {
        this.items = items;
    }
}

