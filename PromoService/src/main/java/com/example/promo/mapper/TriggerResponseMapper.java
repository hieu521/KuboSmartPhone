package com.example.promo.mapper;

import com.example.promo.dto.CampaignTriggerStats;
import com.example.promo.dto.TriggerResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TriggerResponseMapper {

    TriggerResponse fromStats(CampaignTriggerStats stats);
}
