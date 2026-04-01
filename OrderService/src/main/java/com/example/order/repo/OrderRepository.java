package com.example.order.repo;

import com.example.order.domain.OrderStatus;
import com.example.order.model.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    Optional<OrderEntity> findByOrderDraftId(String orderDraftId);
    Optional<OrderEntity> findByOrderDraftIdAndStatus(String orderDraftId, OrderStatus status);
}

