package com.example.product.mapper;

import com.example.product.dto.ProductCreateRequest;
import com.example.product.mapping.ProductMappingHelper;
import com.example.product.model.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ProductMappingHelper.class)
public interface ProductDraftMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", source = "name", qualifiedByName = "trimName")
    Product toNewEntity(ProductCreateRequest request);
}
