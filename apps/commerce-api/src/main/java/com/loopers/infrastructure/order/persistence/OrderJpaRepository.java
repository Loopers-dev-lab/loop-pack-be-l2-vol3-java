package com.loopers.infrastructure.order.persistence;

import java.time.LocalDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.order.Order;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    Slice<Order> findAllByUserIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThan(
            Long userId, 
            LocalDateTime start, 
            LocalDateTime end, 
            Pageable pageable
    );
}