package com.loopers.infrastructure.order.persistence;

import java.time.LocalDateTime;

import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.loopers.domain.order.Order;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

    Slice<Order> findAllByUserIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThan(
            Long userId, 
            LocalDateTime start, 
            LocalDateTime end, 
            Pageable pageable
    );
}