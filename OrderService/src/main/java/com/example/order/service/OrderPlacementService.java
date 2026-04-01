package com.example.order.service;

import com.example.order.client.ProductClient;
import com.example.order.domain.OrderStatus;
import com.example.order.dto.OrderCreateRequest;
import com.example.order.dto.OrderCreateResponse;
import com.example.order.dto.OrderCartCreateRequest;
import com.example.order.dto.OrderCartCreateResponse;
import com.example.order.dto.OrderCartItemRequest;
import com.example.order.dto.OrderCartItemResponse;
import com.example.order.mapping.OrderMappingFallback;
import com.example.order.mapper.OrderResponseMapper;
import com.example.order.mapper.OrderResultMapper;
import com.example.order.mq.OrderCartItemMessage;
import com.example.order.mq.OrderCartItemResultMessage;
import com.example.order.mq.OrderCreatedMessage;
import com.example.order.mq.OrderResultMessage;
import com.example.order.model.OrderEntity;
import com.example.order.repo.OrderRepository;
import com.example.order.redis.LuaResult;
import com.example.order.redis.LuaCartResult;
import com.example.order.redis.OrderLuaService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class OrderPlacementService {

    private final ProductClient productClient;
    private final OrderLuaService orderLuaService;
    private final RabbitTemplate rabbitTemplate;
    private final OrderAwaiter orderAwaiter;
    private final OrderRepository orderRepository;
    private final OrderResponseMapper orderResponseMapper;
    private final OrderResultMapper orderResultMapper;

    @Value("${app.rabbit.order-created}")
    private String orderCreatedQueue;

    @Value("${app.order.confirm-timeout-ms}")
    private long confirmTimeoutMs;

    public OrderPlacementService(
            ProductClient productClient,
            OrderLuaService orderLuaService,
            RabbitTemplate rabbitTemplate,
            OrderAwaiter orderAwaiter,
            OrderRepository orderRepository,
            OrderResponseMapper orderResponseMapper,
            OrderResultMapper orderResultMapper
    ) {
        this.productClient = productClient;
        this.orderLuaService = orderLuaService;
        this.rabbitTemplate = rabbitTemplate;
        this.orderAwaiter = orderAwaiter;
        this.orderRepository = orderRepository;
        this.orderResponseMapper = orderResponseMapper;
        this.orderResultMapper = orderResultMapper;
    }

    /**
     * @param promoId ma kenh: BF2026 (flash sale) hoac RETAIL (mua thuong, khach khong can biet ma KM)
     */
    public ResponseEntity<OrderCreateResponse> placeOrder(String promoId, Long userId, OrderCreateRequest request) {
        Long productId = request.getProductId();
        int qty = request.getQuantity() == null ? 1 : request.getQuantity();
        if (qty != 1) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        BigDecimal priceSnapshot = productClient.getBasePrice(productId);

        String orderDraftId = UUID.randomUUID().toString();
        long ttlSeconds = 600;

        LuaResult luaResult = orderLuaService.tryCreateOrderDraft(promoId, userId, productId, ttlSeconds, orderDraftId);

        String status = luaResult.getStatus();
        long remainingStock = luaResult.getRemainingStock();
        String returnedDraftId = luaResult.getOrderDraftId();

        if ("OK".equals(status)) {
            CompletableFuture<OrderResultMessage> future = orderAwaiter.await(returnedDraftId);

            OrderCreatedMessage message = new OrderCreatedMessage();
            message.setOrderDraftId(returnedDraftId);
            message.setPromoId(promoId);
            message.setUserId(userId);
            message.setProductId(productId);
            message.setQuantity(qty);
            message.setPriceSnapshot(priceSnapshot);

            rabbitTemplate.convertAndSend(orderCreatedQueue, message);

            try {
                OrderResultMessage result = future.get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
                OrderCreateResponse response = orderResultMapper.fromResult(
                        result,
                        new OrderMappingFallback(remainingStock, priceSnapshot)
                );
                HttpStatus httpStatus = result.getStatus() == OrderStatus.CONFIRMED ? HttpStatus.OK : HttpStatus.CONFLICT;
                return new ResponseEntity<>(response, httpStatus);
            } catch (Exception e) {
                orderAwaiter.cleanup(returnedDraftId);
                OrderCreateResponse response = new OrderCreateResponse();
                response.setOrderDraftId(returnedDraftId);
                response.setOrderId(null);
                response.setStatus(OrderStatus.PENDING);
                response.setRemainingStock(remainingStock);
                response.setMessage("Timeout waiting for DB confirmation");
                response.setPriceSnapshot(priceSnapshot);
                return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
            }
        }

        OrderCreateResponse response = new OrderCreateResponse();
        response.setOrderDraftId(returnedDraftId);
        response.setRemainingStock(remainingStock);
        response.setPriceSnapshot(priceSnapshot);

        if ("DUPLICATE".equals(status)) {
            Optional<OrderEntity> existing = orderRepository.findByOrderDraftId(returnedDraftId);
            if (existing.isPresent()) {
                orderResponseMapper.applyFromEntity(response, existing.get());
                response.setMessage("Duplicate order draft, returning existing order");
                return new ResponseEntity<>(response, HttpStatus.OK);
            }
            response.setOrderId(null);
            response.setStatus(OrderStatus.DUPLICATE);
            response.setMessage("User already placed an order for this channel/product in this promo window");
            return new ResponseEntity<>(response, HttpStatus.CONFLICT);
        }

        if ("OUT_OF_STOCK".equals(status)) {
            response.setOrderId(null);
            response.setStatus(OrderStatus.FAILED);
            response.setMessage("Out of stock");
            return new ResponseEntity<>(response, HttpStatus.CONFLICT);
        }

        response.setOrderId(null);
        response.setStatus(OrderStatus.FAILED);
        response.setMessage("Unexpected lua result: " + status);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public ResponseEntity<OrderCartCreateResponse> placeCartOrder(String promoId, Long userId, OrderCartCreateRequest request) {
        List<OrderCartItemRequest> normalizedItems = normalizeCartItems(request.getItems());
        if (normalizedItems.isEmpty()) {
            OrderCartCreateResponse resp = new OrderCartCreateResponse();
            resp.setOrderDraftId(null);
            resp.setOrderId(null);
            resp.setStatus(OrderStatus.FAILED);
            resp.setMessage("Cart items are required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
        }

        // Build price snapshots per cart line (unit price)
        List<OrderCartItemMessage> itemMessages = new ArrayList<>();
        int totalQty = 0;
        BigDecimal totalPrice = BigDecimal.ZERO;
        for (OrderCartItemRequest item : normalizedItems) {
            Long productId = item.getProductId();
            Integer qty = item.getQuantity();
            BigDecimal unitPrice = productClient.getBasePrice(productId);

            OrderCartItemMessage im = new OrderCartItemMessage();
            im.setProductId(productId);
            im.setQuantity(qty);
            im.setPriceSnapshot(unitPrice);
            itemMessages.add(im);

            totalQty += qty;
            totalPrice = totalPrice.add(unitPrice.multiply(BigDecimal.valueOf(qty)));
        }

        String orderDraftId = UUID.randomUUID().toString();
        long ttlSeconds = 600;

        LuaCartResult luaResult = orderLuaService.tryCreateCartOrderDraft(
                promoId,
                userId,
                normalizedItems,
                ttlSeconds,
                orderDraftId
        );

        String status = luaResult.getStatus();
        String returnedDraftId = luaResult.getOrderDraftId();
        List<Long> remainingStocks = luaResult.getRemainingStocks();

        if ("OK".equals(status)) {
            CompletableFuture<OrderResultMessage> future = orderAwaiter.await(returnedDraftId);

            OrderCreatedMessage message = new OrderCreatedMessage();
            message.setOrderDraftId(returnedDraftId);
            message.setPromoId(promoId);
            message.setUserId(userId);
            message.setItems(itemMessages);

            // fallback fields (listener không bắt buộc nhưng set cho rõ)
            message.setProductId(itemMessages.get(0).getProductId());
            message.setQuantity(totalQty);
            message.setPriceSnapshot(totalPrice);

            rabbitTemplate.convertAndSend(orderCreatedQueue, message);

            try {
                OrderResultMessage result = future.get(confirmTimeoutMs, TimeUnit.MILLISECONDS);

                OrderCartCreateResponse resp = new OrderCartCreateResponse();
                resp.setOrderDraftId(returnedDraftId);
                resp.setOrderId(result.getOrderId());
                resp.setStatus(result.getStatus());
                resp.setMessage(result.getMessage());
                resp.setTotalQuantity((long) totalQty);
                resp.setTotalPriceSnapshot(result.getPriceSnapshot());
                resp.setRemainingStock(result.getRemainingStock());

                if (result.getItems() != null) {
                    List<OrderCartItemResponse> items = new ArrayList<>();
                    for (OrderCartItemResultMessage ir : result.getItems()) {
                        OrderCartItemResponse itemResp = new OrderCartItemResponse();
                        itemResp.setProductId(ir.getProductId());
                        itemResp.setQuantity(ir.getQuantity());
                        itemResp.setPriceSnapshot(ir.getPriceSnapshot());
                        itemResp.setRemainingStock(ir.getRemainingStock());
                        items.add(itemResp);
                    }
                    resp.setItems(items);
                }

                HttpStatus httpStatus = result.getStatus() == OrderStatus.CONFIRMED ? HttpStatus.OK : HttpStatus.CONFLICT;
                return new ResponseEntity<>(resp, httpStatus);
            } catch (Exception e) {
                orderAwaiter.cleanup(returnedDraftId);
                OrderCartCreateResponse resp = new OrderCartCreateResponse();
                resp.setOrderDraftId(returnedDraftId);
                resp.setOrderId(null);
                resp.setStatus(OrderStatus.PENDING);
                resp.setMessage("Timeout waiting for DB confirmation");
                resp.setTotalQuantity((long) totalQty);
                resp.setTotalPriceSnapshot(totalPrice);
                resp.setRemainingStock(sumLongs(remainingStocks));
                resp.setItems(buildItemsFromLua(itemMessages, remainingStocks));
                return new ResponseEntity<>(resp, HttpStatus.ACCEPTED);
            }
        }

        OrderCartCreateResponse resp = new OrderCartCreateResponse();
        resp.setOrderDraftId(returnedDraftId);
        resp.setOrderId(null);
        resp.setTotalQuantity((long) totalQty);
        resp.setTotalPriceSnapshot(totalPrice);
        resp.setRemainingStock(sumLongs(remainingStocks));
        resp.setItems(buildItemsFromLua(itemMessages, remainingStocks));

        if ("DUPLICATE".equals(status)) {
            Optional<OrderEntity> existing = orderRepository.findByOrderDraftId(returnedDraftId);
            if (existing.isPresent()) {
                resp.setOrderId(existing.get().getId());
                resp.setStatus(OrderStatus.DUPLICATE);
                resp.setMessage("Duplicate cart order draft, returning existing order");
                return new ResponseEntity<>(resp, HttpStatus.OK);
            }
            resp.setStatus(OrderStatus.DUPLICATE);
            resp.setMessage("Duplicate cart order draft but not found in DB");
            return new ResponseEntity<>(resp, HttpStatus.CONFLICT);
        }

        if ("OUT_OF_STOCK".equals(status)) {
            resp.setStatus(OrderStatus.FAILED);
            resp.setMessage("Out of stock");
            return new ResponseEntity<>(resp, HttpStatus.CONFLICT);
        }

        resp.setStatus(OrderStatus.FAILED);
        resp.setMessage("Unexpected lua result: " + status);
        return new ResponseEntity<>(resp, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static List<OrderCartItemRequest> normalizeCartItems(List<OrderCartItemRequest> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        // Merge duplicates by productId
        Map<Long, Integer> sumByProduct = new HashMap<>();
        for (OrderCartItemRequest it : items) {
            if (it == null || it.getProductId() == null || it.getQuantity() == null) continue;
            sumByProduct.merge(it.getProductId(), it.getQuantity(), Integer::sum);
        }

        return sumByProduct.entrySet().stream()
                .sorted(Comparator.comparingLong(Map.Entry::getKey))
                .map(e -> {
                    OrderCartItemRequest r = new OrderCartItemRequest();
                    r.setProductId(e.getKey());
                    r.setQuantity(e.getValue());
                    return r;
                })
                .toList();
    }

    private static long sumLongs(List<Long> values) {
        if (values == null) return 0;
        long s = 0;
        for (Long v : values) {
            s += v == null ? 0 : v;
        }
        return s;
    }

    private static List<OrderCartItemResponse> buildItemsFromLua(
            List<OrderCartItemMessage> itemMessages,
            List<Long> remainingStocks
    ) {
        List<OrderCartItemResponse> items = new ArrayList<>();
        int n = itemMessages == null ? 0 : itemMessages.size();
        for (int i = 0; i < n; i++) {
            OrderCartItemMessage im = itemMessages.get(i);
            OrderCartItemResponse ir = new OrderCartItemResponse();
            ir.setProductId(im.getProductId());
            ir.setQuantity(im.getQuantity());
            ir.setPriceSnapshot(im.getPriceSnapshot());

            Long remaining = (remainingStocks != null && remainingStocks.size() > i) ? remainingStocks.get(i) : 0L;
            ir.setRemainingStock(remaining);
            items.add(ir);
        }
        return items;
    }
}
