package com.example.promo.mq;

import com.example.promo.dto.CampaignAppliedMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CampaignPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbit.queue.campaign-applied}")
    private String campaignAppliedQueue;

    public CampaignPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishCampaignApplied(CampaignAppliedMessage message) {
        // Simple queue publish: use default exchange + routing key = queue name
        rabbitTemplate.convertAndSend(campaignAppliedQueue, message);
    }
}

