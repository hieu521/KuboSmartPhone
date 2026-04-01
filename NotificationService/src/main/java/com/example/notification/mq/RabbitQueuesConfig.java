package com.example.notification.mq;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitQueuesConfig {

    @Bean
    public Queue campaignAppliedQueue(@Value("${app.rabbit.queue.campaign-applied}") String queueName) {
        // Durable queue để không mất message nếu service restart
        return new Queue(queueName, true);
    }
}

