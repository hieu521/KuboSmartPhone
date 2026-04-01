package com.example.promo.mapping;

import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
public class PromoMappingHelper {

    @Named("longToUserIdString")
    public String longToUserIdString(Long userId) {
        return userId == null ? null : userId.toString();
    }
}
