package com.example.order.mq;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitQueuesConfig {

    @Bean
    public Queue orderCreatedQueue(@Value("${app.rabbit.order-created}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue orderResultQueue(@Value("${app.rabbit.order-result}") String queueName) {
        return new Queue(queueName, true);
    }
}

