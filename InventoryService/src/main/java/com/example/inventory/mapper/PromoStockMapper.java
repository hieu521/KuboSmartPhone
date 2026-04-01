package com.example.inventory.mapper;

import com.example.inventory.dto.PromoStockResponse;
import com.example.inventory.mapping.PromoStockMappingHelper;
import com.example.inventory.model.PromoStock;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Abstract mapper: cho phép dùng expression gọi bean inject được (field {@code promoStockMappingHelper}).
 */
@Mapper(componentModel = "spring")
public abstract class PromoStockMapper {

    @Autowired
    protected PromoStockMappingHelper promoStockMappingHelper;

    @Mapping(target = "redisKey", expression = "java(promoStockMappingHelper.redisKeyFromEntity(entity))")
    public abstract PromoStockResponse toResponse(PromoStock entity);
}
