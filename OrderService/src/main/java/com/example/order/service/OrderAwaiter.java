package com.example.order.service;

import com.example.order.mq.OrderResultMessage;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderAwaiter {

    private final ConcurrentHashMap<String, CompletableFuture<OrderResultMessage>> futures = new ConcurrentHashMap<>();

    public CompletableFuture<OrderResultMessage> await(String orderDraftId) {
        CompletableFuture<OrderResultMessage> future = new CompletableFuture<>();
        CompletableFuture<OrderResultMessage> existing = futures.putIfAbsent(orderDraftId, future);
        return existing != null ? existing : future;
    }

    public void complete(OrderResultMessage message) {
        if (message == null || message.getOrderDraftId() == null) {
            return;
        }
        CompletableFuture<OrderResultMessage> future = futures.remove(message.getOrderDraftId());
        if (future != null) {
            future.complete(message);
        }
    }

    public void cleanup(String orderDraftId) {
        if (orderDraftId != null) {
            futures.remove(orderDraftId);
        }
    }
}

