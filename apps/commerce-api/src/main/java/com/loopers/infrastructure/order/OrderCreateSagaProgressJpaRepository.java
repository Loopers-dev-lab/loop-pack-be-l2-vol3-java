package com.loopers.infrastructure.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderCreateSagaProgressJpaRepository extends JpaRepository<OrderCreateSagaProgressEntity, UUID> {
}
