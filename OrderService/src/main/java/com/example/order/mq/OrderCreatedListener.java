package com.example.order.mq;

import com.example.order.domain.OrderStatus;
import com.example.order.model.OrderItemEntity;
import com.example.order.model.OrderEntity;
import com.example.order.repo.OrderRepository;
import com.example.order.repo.OrderItemRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class OrderCreatedListener {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.rabbit.order-result}")
    private String orderResultQueue;

    public OrderCreatedListener(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            RabbitTemplate rabbitTemplate,
            StringRedisTemplate redisTemplate
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
    }

    @RabbitListener(queues = "${app.rabbit.order-created}")
    public void onCreate(OrderCreatedMessage message) {
        if (message == null || message.getOrderDraftId() == null) {
            return;
        }

        String orderDraftId = message.getOrderDraftId();
        try {
            boolean hasItems = message.getItems() != null && !message.getItems().isEmpty();
            List<OrderCartItemMessage> items = new ArrayList<>();
            if (hasItems) {
                items.addAll(message.getItems());
            } else {
                // Backward compatibility for single-item producer
                OrderCartItemMessage item = new OrderCartItemMessage();
                item.setProductId(message.getProductId());
                item.setQuantity(message.getQuantity());
                item.setPriceSnapshot(message.getPriceSnapshot());
                items.add(item);
            }

            long totalRemainingForResponse = 0;
            int totalQty = items.stream().mapToInt(OrderCartItemMessage::getQuantity).sum();
            BigDecimal totalPrice = items.stream()
                    .map(i -> i.getPriceSnapshot().multiply(BigDecimal.valueOf(i.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Long headerProductId = items.get(0).getProductId();

            OrderEntity entity = orderRepository.findByOrderDraftId(orderDraftId)
                    .orElseGet(() -> {
                        OrderEntity o = new OrderEntity();
                        o.setOrderDraftId(message.getOrderDraftId());
                        o.setUserId(message.getUserId());
                        o.setPromoId(message.getPromoId());
                        o.setProductId(headerProductId);
                        o.setQuantity(totalQty);
                        o.setPriceSnapshot(totalPrice);
                        return o;
                    });

            if (entity.getId() == null) {
                entity.setStatus(OrderStatus.CONFIRMED);
                entity.setConfirmedAt(Instant.now());
                orderRepository.save(entity);

                // lưu order items (cart chuẩn)
                if (hasItems) {
                    for (OrderCartItemMessage it : items) {
                        OrderItemEntity itemEntity = new OrderItemEntity();
                        itemEntity.setOrderDraftId(orderDraftId);
                        itemEntity.setProductId(it.getProductId());
                        itemEntity.setQuantity(it.getQuantity());
                        itemEntity.setPriceSnapshot(it.getPriceSnapshot());
                        orderItemRepository.save(itemEntity);
                    }
                }
            }

            OrderResultMessage result = new OrderResultMessage();
            result.setOrderDraftId(orderDraftId);
            result.setOrderId(entity.getId());
            result.setStatus(entity.getStatus());

            // response của single / cart:
            // - remainingStock và priceSnapshot: tổng (để mapper single không vỡ)
            // - items[]: chi tiết theo từng product khi là cart
            List<OrderCartItemResultMessage> itemResults = new ArrayList<>();
            if (hasItems) {
                for (OrderCartItemMessage it : items) {
                    long remaining = remainingStock(message.getPromoId(), it.getProductId());
                    totalRemainingForResponse += remaining;

                    OrderCartItemResultMessage ir = new OrderCartItemResultMessage();
                    ir.setProductId(it.getProductId());
                    ir.setQuantity(it.getQuantity());
                    ir.setPriceSnapshot(it.getPriceSnapshot());
                    ir.setRemainingStock(remaining);
                    itemResults.add(ir);
                }

                result.setItems(itemResults);
            } else {
                long remaining = remainingStock(message.getPromoId(), message.getProductId());
                totalRemainingForResponse = remaining;

                // giữ items null cho mapper single (nếu muốn)
            }

            result.setRemainingStock(totalRemainingForResponse);
            result.setMessage("Order confirmed");
            result.setPriceSnapshot(entity.getPriceSnapshot());

            rabbitTemplate.convertAndSend(orderResultQueue, result);
        } catch (Exception e) {
            OrderResultMessage result = new OrderResultMessage();
            result.setOrderDraftId(orderDraftId);
            result.setOrderId(null);
            result.setStatus(OrderStatus.FAILED);

            boolean hasItems = message.getItems() != null && !message.getItems().isEmpty();
            if (hasItems) {
                List<OrderCartItemResultMessage> itemResults = new ArrayList<>();
                long totalRemaining = 0;
                for (OrderCartItemMessage it : message.getItems()) {
                    long remaining = remainingStock(message.getPromoId(), it.getProductId());
                    totalRemaining += remaining;

                    OrderCartItemResultMessage ir = new OrderCartItemResultMessage();
                    ir.setProductId(it.getProductId());
                    ir.setQuantity(it.getQuantity());
                    ir.setPriceSnapshot(it.getPriceSnapshot());
                    ir.setRemainingStock(remaining);
                    itemResults.add(ir);
                }
                result.setItems(itemResults);
                result.setRemainingStock(totalRemaining);

                BigDecimal totalPrice = message.getItems().stream()
                        .map(i -> i.getPriceSnapshot().multiply(BigDecimal.valueOf(i.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                result.setPriceSnapshot(totalPrice);
            } else {
                result.setRemainingStock(remainingStock(message.getPromoId(), message.getProductId()));
                result.setPriceSnapshot(message.getPriceSnapshot());
            }

            result.setMessage("Order failed: " + e.getMessage());
            rabbitTemplate.convertAndSend(orderResultQueue, result);
        }
    }

    private long remainingStock(String promoId, Long productId) {
        try {
            String key = "promo:stock:" + promoId + ":" + productId;
            String v = redisTemplate.opsForValue().get(key);
            return v == null ? 0 : Long.parseLong(v);
        } catch (Exception ignored) {
            return 0;
        }
    }
}

