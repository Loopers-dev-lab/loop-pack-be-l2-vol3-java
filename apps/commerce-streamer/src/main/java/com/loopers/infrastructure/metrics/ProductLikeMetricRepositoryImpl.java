package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductLikeMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ProductLikeMetricRepositoryImpl implements ProductLikeMetricRepository {

    private final ProductLikeMetricJpaRepository jpaRepository;

    @Override
    @Transactional
    public void upsert(Long productId, LocalDateTime bucketTime, int delta) {
        jpaRepository.upsert(productId, bucketTime, delta);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> sumByBucketTimeRange(LocalDateTime from, LocalDateTime to, int limit) {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (Object[] row : jpaRepository.sumByBucketTimeRange(from, to, limit)) {
            result.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return result;
    }
}
