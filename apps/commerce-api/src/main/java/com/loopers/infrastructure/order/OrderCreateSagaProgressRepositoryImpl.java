package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderCreateSagaProgress;
import com.loopers.domain.order.OrderCreateSagaProgressRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class OrderCreateSagaProgressRepositoryImpl implements OrderCreateSagaProgressRepository {
    private final OrderCreateSagaProgressJpaRepository repository;
    public OrderCreateSagaProgressRepositoryImpl(OrderCreateSagaProgressJpaRepository repository) { this.repository = repository; }

    @Override
    public OrderCreateSagaProgress save(OrderCreateSagaProgress progress) {
        OrderCreateSagaProgressEntity e = repository.findById(progress.orderId()).map(found -> { found.updateFrom(progress); return found; }).orElseGet(() -> OrderCreateSagaProgressEntity.from(progress));
        return repository.save(e).toDomain();
    }

    @Override
    public Optional<OrderCreateSagaProgress> findByOrderId(UUID orderId) {
        return repository.findById(orderId).map(OrderCreateSagaProgressEntity::toDomain);
    }
}
