package com.example.order.redis;

import com.example.order.dto.OrderCartItemRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dedup signature for cart:
 * - Normalize items by summing quantities per productId
 * - Sort items by productId ascending
 * - Signature format: pId:qty|pId:qty|...
 * - Dedup key uses hash(signature) để key ngắn.
 */
public final class OrderCartSignature {

    private OrderCartSignature() {}

    public static String normalizedSignature(List<OrderCartItemRequest> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }

        Map<Long, Integer> sumByProduct = items.stream()
                .filter(i -> i != null && i.getProductId() != null && i.getQuantity() != null)
                .collect(Collectors.toMap(
                        OrderCartItemRequest::getProductId,
                        OrderCartItemRequest::getQuantity,
                        Integer::sum
                ));

        return sumByProduct.entrySet().stream()
                .sorted(Comparator.comparingLong(Map.Entry::getKey))
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining("|"));
    }

    public static String sha256Hex(String input) {
        if (input == null) {
            input = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 always exists in JRE; fallback để tránh crash.
            return Integer.toHexString(input.hashCode());
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

