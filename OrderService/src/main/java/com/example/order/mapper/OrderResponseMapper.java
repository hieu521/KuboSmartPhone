package com.example.order.mapper;

import com.example.order.dto.OrderCreateResponse;
import com.example.order.model.OrderEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface OrderResponseMapper {

    @Mapping(target = "orderId", source = "id")
    @Mapping(target = "remainingStock", ignore = true)
    @Mapping(target = "message", ignore = true)
    void applyFromEntity(@MappingTarget OrderCreateResponse response, OrderEntity entity);
}
