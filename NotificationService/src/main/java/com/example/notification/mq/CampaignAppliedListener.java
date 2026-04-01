package com.example.notification.mq;

import com.example.notification.service.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CampaignAppliedListener {

    private static final Logger log = LoggerFactory.getLogger(CampaignAppliedListener.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final MailService mailService;

    private final String wsDestination = "/queue/campaign.applied";

    public CampaignAppliedListener(SimpMessagingTemplate messagingTemplate, MailService mailService) {
        this.messagingTemplate = messagingTemplate;
        this.mailService = mailService;
    }

    @RabbitListener(
            queues = "${app.rabbit.queue.campaign-applied}",
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void onMessage(CampaignAppliedMessage message) {
        if (message == null || message.getUsers() == null) {
            return;
        }

        for (UserRef user : message.getUsers()) {
            String userId = user.getUserId();
            if (userId == null) continue;

            // WebSocket push
            messagingTemplate.convertAndSendToUser(userId, wsDestination, message);

            // Mail send (Black Friday)
            try {
                mailService.sendBlackFridayMail(user, message.getCampaignId());
            } catch (Exception ex) {
                log.warn("Failed to send mail for userId={}, email={}", userId, user.getEmail(), ex);
                // Production: push to DLQ / retry strategy
            }
        }
    }
}

