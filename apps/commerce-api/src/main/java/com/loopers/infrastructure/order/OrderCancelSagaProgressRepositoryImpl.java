package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderCancelSagaProgress;
import com.loopers.domain.order.OrderCancelSagaProgressRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public class OrderCancelSagaProgressRepositoryImpl implements OrderCancelSagaProgressRepository {

    private final OrderCancelSagaProgressJpaRepository repository;

    public OrderCancelSagaProgressRepositoryImpl(OrderCancelSagaProgressJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public OrderCancelSagaProgress save(OrderCancelSagaProgress progress) {
        OrderCancelSagaProgressEntity entity = repository.findById(progress.orderId())
                .map(found -> {
                    found.updateFrom(progress);
                    return found;
                })
                .orElseGet(() -> OrderCancelSagaProgressEntity.from(progress));
        return repository.save(entity).toDomain();
    }

    @Override
    public Optional<OrderCancelSagaProgress> findByOrderId(UUID orderId) {
        return repository.findById(orderId).map(OrderCancelSagaProgressEntity::toDomain);
    }

    @Override
    public List<OrderCancelSagaProgress> findRetryCandidates(int maxRetryCount) {
        return repository.findAllByLastErrorIsNotNullAndRetryCountLessThan(maxRetryCount)
                .stream()
                .map(OrderCancelSagaProgressEntity::toDomain)
                .toList();
    }
}
