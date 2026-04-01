package com.example.promo.dto;

import java.util.List;

public class CampaignAppliedMessage {
    private String campaignId;
    private String eventType;
    private List<UserRef> users;

    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public List<UserRef> getUsers() {
        return users;
    }

    public void setUsers(List<UserRef> users) {
        this.users = users;
    }
}

