package com.example.promo.mapper;

import com.example.promo.dto.InternalUserDto;
import com.example.promo.dto.UserRef;
import com.example.promo.mapping.PromoMappingHelper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = PromoMappingHelper.class)
public interface UserRefMapper {

    @Mapping(target = "userId", source = "userId", qualifiedByName = "longToUserIdString")
    UserRef toUserRef(InternalUserDto dto);
}
