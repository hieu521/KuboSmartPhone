package com.example.promo.service;

import com.example.promo.client.AuthServiceClient;
import com.example.promo.dto.CampaignAppliedMessage;
import com.example.promo.dto.CampaignTriggerStats;
import com.example.promo.dto.InternalUserDto;
import com.example.promo.dto.TriggerResponse;
import com.example.promo.dto.UserRef;
import com.example.promo.mapper.TriggerResponseMapper;
import com.example.promo.mapper.UserRefMapper;
import com.example.promo.mq.CampaignPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CampaignTriggerService {

    private final AuthServiceClient authServiceClient;
    private final CampaignPublisher campaignPublisher;
    private final UserRefMapper userRefMapper;
    private final TriggerResponseMapper triggerResponseMapper;

    @Value("${app.auth-service.admin-email}")
    private String adminEmail;

    @Value("${app.auth-service.admin-password}")
    private String adminPassword;

    @Value("${app.promo.chunk-size}")
    private int chunkSize;

    public CampaignTriggerService(
            AuthServiceClient authServiceClient,
            CampaignPublisher campaignPublisher,
            UserRefMapper userRefMapper,
            TriggerResponseMapper triggerResponseMapper
    ) {
        this.authServiceClient = authServiceClient;
        this.campaignPublisher = campaignPublisher;
        this.userRefMapper = userRefMapper;
        this.triggerResponseMapper = triggerResponseMapper;
    }

    public TriggerResponse triggerBlackFriday(String campaignId) {
        String accessToken = authServiceClient.loginAndGetAccessToken(adminEmail, adminPassword);
        List<InternalUserDto> users = authServiceClient.getUsersForCampaign(accessToken, "ROLE_USER");

        int total = users == null ? 0 : users.size();
        if (total == 0) {
            return triggerResponseMapper.fromStats(new CampaignTriggerStats(campaignId, 0, chunkSize, 0));
        }

        int chunksPublished = 0;
        for (int i = 0; i < total; i += chunkSize) {
            int end = Math.min(total, i + chunkSize);
            List<InternalUserDto> chunk = users.subList(i, end);

            List<UserRef> userRefs = chunk.stream()
                    .filter(u -> u.getUserId() != null)
                    .map(userRefMapper::toUserRef)
                    .toList();

            CampaignAppliedMessage message = new CampaignAppliedMessage();
            message.setCampaignId(campaignId);
            message.setEventType("campaign.applied");
            message.setUsers(userRefs);

            campaignPublisher.publishCampaignApplied(message);
            chunksPublished++;
        }

        return triggerResponseMapper.fromStats(new CampaignTriggerStats(campaignId, total, chunkSize, chunksPublished));
    }
}

