package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductViewMetric;
import com.loopers.domain.metrics.ProductViewMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ProductViewMetricRepositoryImpl implements ProductViewMetricRepository {

    private final ProductViewMetricJpaRepository jpaRepository;

    @Override
    @Transactional
    public void batchUpsert(List<ProductViewMetric> metrics) {
        for (ProductViewMetric metric : metrics) {
            jpaRepository.upsert(metric.getProductId(), metric.getBucketTime(), metric.getViewCount());
        }
    }

    @Override
    @Transactional
    public void upsert(Long productId, LocalDateTime bucketTime, long viewCount) {
        jpaRepository.upsert(productId, bucketTime, viewCount);
    }
}
