package com.loopers.domain.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * product_metrics 집계 서비스.
 *
 * 모든 카운터 연산은 INSERT ... ON DUPLICATE KEY UPDATE 원자적 쿼리로 처리한다.
 * @Version 낙관적 락 불필요, 재시도 로직 불필요.
 */
@RequiredArgsConstructor
@Component
public class ProductMetricsService {

    private final ProductMetricsRepository productMetricsRepository;

    public void incrementLikeCount(Long productId) {
        productMetricsRepository.incrementLikeCount(productId);
    }

    public void decrementLikeCount(Long productId) {
        productMetricsRepository.decrementLikeCount(productId);
    }

    public void incrementOrderCount(Long productId) {
        productMetricsRepository.incrementOrderCount(productId);
    }

    public void incrementViewCount(Long productId) {
        productMetricsRepository.incrementViewCount(productId);
    }
}
