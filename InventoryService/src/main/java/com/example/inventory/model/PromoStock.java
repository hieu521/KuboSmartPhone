package com.example.inventory.model;

import jakarta.persistence.*;

@Entity
@Table(
        name = "promo_stock",
        uniqueConstraints = @UniqueConstraint(name = "uk_promo_product", columnNames = {"promo_id", "product_id"})
)
public class PromoStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "promo_id", nullable = false, length = 100)
    private String promoId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long stock;

    public Long getId() {
        return id;
    }

    public String getPromoId() {
        return promoId;
    }

    public void setPromoId(String promoId) {
        this.promoId = promoId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getStock() {
        return stock;
    }

    public void setStock(Long stock) {
        this.stock = stock;
    }
}

