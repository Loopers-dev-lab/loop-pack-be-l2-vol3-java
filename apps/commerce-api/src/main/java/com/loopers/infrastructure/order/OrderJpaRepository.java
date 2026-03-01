package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "orderItems")
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = "orderItems")
    List<Order> findAllByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long userId, ZonedDateTime startAt, ZonedDateTime endAt);

    @Override
    @EntityGraph(attributePaths = "orderItems")
    Optional<Order> findById(Long id);

    @Override
    @EntityGraph(attributePaths = "orderItems")
    Page<Order> findAll(Pageable pageable);
}
