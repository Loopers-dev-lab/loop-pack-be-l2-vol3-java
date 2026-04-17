package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductViewMetric;
import com.loopers.domain.metrics.ProductViewMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> sumByBucketTimeRange(LocalDateTime from, LocalDateTime to, int limit) {
        return toMap(jpaRepository.sumByBucketTimeRange(from, to, limit));
    }

    private Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Long productId = ((Number) row[0]).longValue();
            Long total = ((Number) row[1]).longValue();
            result.put(productId, total);
        }
        return result;
    }
}
