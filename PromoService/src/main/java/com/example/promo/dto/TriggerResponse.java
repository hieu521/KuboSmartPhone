package com.example.promo.dto;

public class TriggerResponse {
    private String campaignId;
    private int totalUsers;
    private int chunkSize;
    private int chunksPublished;

    public TriggerResponse() {
    }

    public TriggerResponse(String campaignId, int totalUsers, int chunkSize, int chunksPublished) {
        this.campaignId = campaignId;
        this.totalUsers = totalUsers;
        this.chunkSize = chunkSize;
        this.chunksPublished = chunksPublished;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    public int getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(int totalUsers) {
        this.totalUsers = totalUsers;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunksPublished() {
        return chunksPublished;
    }

    public void setChunksPublished(int chunksPublished) {
        this.chunksPublished = chunksPublished;
    }
}

