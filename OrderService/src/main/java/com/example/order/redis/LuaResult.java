package com.example.order.redis;

public class LuaResult {
    private String status;
    private String orderDraftId;
    private long remainingStock;

    public LuaResult(String status, String orderDraftId, long remainingStock) {
        this.status = status;
        this.orderDraftId = orderDraftId;
        this.remainingStock = remainingStock;
    }

    public String getStatus() {
        return status;
    }

    public String getOrderDraftId() {
        return orderDraftId;
    }

    public long getRemainingStock() {
        return remainingStock;
    }
}

