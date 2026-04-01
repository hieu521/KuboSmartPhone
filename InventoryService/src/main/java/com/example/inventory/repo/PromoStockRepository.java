package com.example.inventory.repo;

import com.example.inventory.model.PromoStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromoStockRepository extends JpaRepository<PromoStock, Long> {
    Optional<PromoStock> findByPromoIdAndProductId(String promoId, Long productId);
}

