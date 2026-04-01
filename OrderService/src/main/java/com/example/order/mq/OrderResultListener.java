package com.example.order.mq;

import com.example.order.service.OrderAwaiter;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderResultListener {

    private final OrderAwaiter orderAwaiter;

    public OrderResultListener(OrderAwaiter orderAwaiter) {
        this.orderAwaiter = orderAwaiter;
    }

    @RabbitListener(queues = "${app.rabbit.order-result}")
    public void onResult(OrderResultMessage message) {
        orderAwaiter.complete(message);
    }
}

