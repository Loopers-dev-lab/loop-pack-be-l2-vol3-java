package com.loopers.domain.order;

import java.util.Optional;
import java.util.UUID;

public interface OrderCreateSagaProgressRepository {
    OrderCreateSagaProgress save(OrderCreateSagaProgress progress);

    Optional<OrderCreateSagaProgress> findByOrderId(UUID orderId);
}
