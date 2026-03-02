package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    @Override
    @EntityGraph(attributePaths = {"orderItems"})
    Optional<Order> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findAll();

    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByUserId(Long userId);
}
