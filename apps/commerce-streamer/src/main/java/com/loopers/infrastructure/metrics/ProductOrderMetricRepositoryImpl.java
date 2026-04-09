package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductOrderMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class ProductOrderMetricRepositoryImpl implements ProductOrderMetricRepository {

    private final ProductOrderMetricJpaRepository jpaRepository;

    @Override
    @Transactional
    public void upsert(Long productId, LocalDateTime bucketTime, int orderDelta, long qtyDelta, long amountDelta) {
        jpaRepository.upsert(productId, bucketTime, orderDelta, qtyDelta, amountDelta);
    }
}
