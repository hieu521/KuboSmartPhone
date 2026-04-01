package com.example.order.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

import com.example.order.dto.OrderCartItemRequest;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class OrderLuaService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> luaScript;
    private final DefaultRedisScript<List> cartLuaScript;

    public OrderLuaService(
            StringRedisTemplate redisTemplate,
            @Qualifier("orderCreateLuaScript") DefaultRedisScript<List> orderCreateLuaScript,
            @Qualifier("orderCartCreateLuaScript") DefaultRedisScript<List> orderCartCreateLuaScript
    ) {
        this.redisTemplate = redisTemplate;
        this.luaScript = orderCreateLuaScript;
        this.cartLuaScript = orderCartCreateLuaScript;
    }

    public LuaResult tryCreateOrderDraft(
            String promoId,
            Long userId,
            Long productId,
            long ttlSeconds,
            String orderDraftId
    ) {
        String dedupKey = dedupKey(promoId, userId);
        String stockKey = stockKey(promoId, productId);
        String pendingKey = pendingKey(orderDraftId);

        List<String> keys = List.of(dedupKey, stockKey, pendingKey);
        // result: { status, draftId, remainingStock }
        List result = redisTemplate.execute(luaScript, keys, orderDraftId, String.valueOf(ttlSeconds));
        if (result == null || result.isEmpty()) {
            return new LuaResult("ERROR", orderDraftId, 0);
        }

        String status = String.valueOf(result.get(0));
        String returnedDraftId = result.size() > 1 ? String.valueOf(result.get(1)) : orderDraftId;
        long remainingStock = 0;
        if (result.size() > 2) {
            try {
                remainingStock = Long.parseLong(String.valueOf(result.get(2)));
            } catch (Exception ignored) {
                remainingStock = 0;
            }
        }
        return new LuaResult(status, returnedDraftId, remainingStock);
    }

    public LuaCartResult tryCreateCartOrderDraft(
            String promoId,
            Long userId,
            List<OrderCartItemRequest> itemsSorted,
            long ttlSeconds,
            String orderDraftId
    ) {
        // itemsSorted cần được normalize: unique productId và sort tăng dần theo productId
        String signature = OrderCartSignature.normalizedSignature(itemsSorted);
        String itemsHash = OrderCartSignature.sha256Hex(signature);

        String dedupKey = "order:dedup:" + promoId + ":" + userId + ":" + itemsHash;
        String pendingKey = pendingKey(orderDraftId);

        List<String> stockKeys = itemsSorted.stream()
                .map(i -> stockKey(promoId, i.getProductId()))
                .collect(Collectors.toList());

        List<String> keys = new ArrayList<>(2 + stockKeys.size());
        keys.add(dedupKey);
        keys.add(pendingKey);
        keys.addAll(stockKeys);

        // ARGV: [orderDraftId, ttlSeconds, qty1..qtyN]
        Object[] args = new Object[2 + itemsSorted.size()];
        args[0] = orderDraftId;
        args[1] = String.valueOf(ttlSeconds);
        for (int i = 0; i < itemsSorted.size(); i++) {
            args[2 + i] = String.valueOf(itemsSorted.get(i).getQuantity());
        }

        List result = redisTemplate.execute(cartLuaScript, keys, args);
        if (result == null || result.isEmpty()) {
            return new LuaCartResult("ERROR", orderDraftId, List.of());
        }

        String status = String.valueOf(result.get(0));
        String returnedDraftId = result.size() > 1 ? String.valueOf(result.get(1)) : orderDraftId;

        int n = itemsSorted.size();
        List<Long> remainingStocks = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int idx = 2 + i;
            long v = 0;
            if (result.size() > idx && result.get(idx) != null) {
                try {
                    v = Long.parseLong(String.valueOf(result.get(idx)));
                } catch (Exception ignored) {
                    v = 0;
                }
            }
            remainingStocks.add(v);
        }

        return new LuaCartResult(status, returnedDraftId, remainingStocks);
    }

    private static String dedupKey(String promoId, Long userId) {
        return "order:dedup:" + promoId + ":" + userId;
    }

    private static String stockKey(String promoId, Long productId) {
        return "promo:stock:" + promoId + ":" + productId;
    }

    private static String pendingKey(String orderDraftId) {
        return "order:pending:" + orderDraftId;
    }
}

