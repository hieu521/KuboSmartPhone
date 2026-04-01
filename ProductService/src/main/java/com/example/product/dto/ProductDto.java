package com.example.product.dto;

import java.math.BigDecimal;

public class ProductDto {
    private Long id;
    private String name;
    private BigDecimal basePrice;
    private boolean active;

    public ProductDto() {
    }

    public ProductDto(Long id, String name, BigDecimal basePrice, boolean active) {
        this.id = id;
        this.name = name;
        this.basePrice = basePrice;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}

