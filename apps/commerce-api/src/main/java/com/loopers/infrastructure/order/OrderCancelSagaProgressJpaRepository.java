package com.loopers.infrastructure.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderCancelSagaProgressJpaRepository extends JpaRepository<OrderCancelSagaProgressEntity, UUID> {
    List<OrderCancelSagaProgressEntity> findAllByLastErrorIsNotNullAndRetryCountLessThan(int retryCount);
}
