package com.loopers.domain.order;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface OrderCancelSagaProgressRepository {
    OrderCancelSagaProgress save(OrderCancelSagaProgress progress);

    Optional<OrderCancelSagaProgress> findByOrderId(UUID orderId);

    List<OrderCancelSagaProgress> findRetryCandidates(int maxRetryCount);
}
