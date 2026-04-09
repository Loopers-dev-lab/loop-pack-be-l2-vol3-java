package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductLikeMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class ProductLikeMetricRepositoryImpl implements ProductLikeMetricRepository {

    private final ProductLikeMetricJpaRepository jpaRepository;

    @Override
    @Transactional
    public void upsert(Long productId, LocalDateTime bucketTime, int delta) {
        jpaRepository.upsert(productId, bucketTime, delta);
    }
}
