package com.example.product.mapping;

import org.mapstruct.Named;
import org.springframework.stereotype.Component;

/**
 * Chuẩn hoá text / rule nhỏ khi map request → entity (không nhồi vào interface mapper).
 */
@Component
public class ProductMappingHelper {

    @Named("trimName")
    public String trimName(String name) {
        return name == null ? null : name.trim();
    }
}
