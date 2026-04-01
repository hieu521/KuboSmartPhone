package com.example.product.mapper;

import com.example.product.dto.ProductDto;
import com.example.product.model.Product;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductDto toDto(Product product);
}
