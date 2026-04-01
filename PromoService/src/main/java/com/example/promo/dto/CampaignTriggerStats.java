package com.example.promo.dto;

/**
 * Snapshot tham số trước khi map sang {@link TriggerResponse} (tách khỏi service).
 */
public record CampaignTriggerStats(String campaignId, int totalUsers, int chunkSize, int chunksPublished) {}
