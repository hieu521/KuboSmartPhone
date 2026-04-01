package com.example.order.redis;

import java.util.List;

public class LuaCartResult {
    private final String status; // OK | DUPLICATE | OUT_OF_STOCK | ERROR
    private final String orderDraftId;
    private final List<Long> remainingStocks; // align với itemsSorted (cùng thứ tự)

    public LuaCartResult(String status, String orderDraftId, List<Long> remainingStocks) {
        this.status = status;
        this.orderDraftId = orderDraftId;
        this.remainingStocks = remainingStocks;
    }

    public String getStatus() {
        return status;
    }

    public String getOrderDraftId() {
        return orderDraftId;
    }

    public List<Long> getRemainingStocks() {
        return remainingStocks;
    }
}

