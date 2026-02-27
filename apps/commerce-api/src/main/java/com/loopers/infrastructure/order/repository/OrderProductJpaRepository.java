package com.loopers.infrastructure.order.repository;

import com.loopers.infrastructure.order.entity.OrderProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderProductJpaRepository extends JpaRepository<OrderProductEntity, Long> {

    List<OrderProductEntity> findAllByOrderId(Long orderId);
}
