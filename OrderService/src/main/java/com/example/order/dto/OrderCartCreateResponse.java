package com.example.order.dto;

import com.example.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public class OrderCartCreateResponse {

    private String orderDraftId;
    private Long orderId;
    private OrderStatus status;

    private List<OrderCartItemResponse> items;

    private Long totalQuantity;
    private BigDecimal totalPriceSnapshot;

    private Long remainingStock; // optional legacy/display convenience (sum remaining) - can be null
    private String message;

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

    public List<OrderCartItemResponse> getItems() {
        return items;
    }

    public void setItems(List<OrderCartItemResponse> items) {
        this.items = items;
    }

    public Long getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Long totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public BigDecimal getTotalPriceSnapshot() {
        return totalPriceSnapshot;
    }

    public void setTotalPriceSnapshot(BigDecimal totalPriceSnapshot) {
        this.totalPriceSnapshot = totalPriceSnapshot;
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
}

