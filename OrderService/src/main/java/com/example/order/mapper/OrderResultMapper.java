package com.example.order.mapper;

import com.example.order.dto.OrderCreateResponse;
import com.example.order.mapping.OrderMappingFallback;
import com.example.order.mapping.OrderMappingHelper;
import com.example.order.mq.OrderResultMessage;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = OrderMappingHelper.class)
public interface OrderResultMapper {

    @Mapping(target = "remainingStock", source = "remainingStock", qualifiedByName = "remainingOrFallback")
    @Mapping(target = "priceSnapshot", source = "priceSnapshot", qualifiedByName = "priceOrFallback")
    OrderCreateResponse fromResult(OrderResultMessage result, @Context OrderMappingFallback fallback);
}
