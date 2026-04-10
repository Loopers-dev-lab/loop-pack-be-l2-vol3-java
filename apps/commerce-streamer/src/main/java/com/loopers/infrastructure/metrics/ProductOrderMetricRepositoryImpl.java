package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductOrderMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ProductOrderMetricRepositoryImpl implements ProductOrderMetricRepository {

    private final ProductOrderMetricJpaRepository jpaRepository;

    @Override
    @Transactional
    public void upsert(Long productId, LocalDateTime bucketTime, int orderDelta, long qtyDelta, long amountDelta) {
        jpaRepository.upsert(productId, bucketTime, orderDelta, qtyDelta, amountDelta);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> sumQuantityByBucketTimeRange(LocalDateTime from, LocalDateTime to, int limit) {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (Object[] row : jpaRepository.sumQuantityByBucketTimeRange(from, to, limit)) {
            result.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return result;
    }
}
