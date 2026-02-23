package com.loopers.infrastructure.order.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.order.Order;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {
}