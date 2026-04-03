package com.loopers.application.metrics;

import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final ProductMetricsRepository productMetricsRepository;

    public void incrementLikeCount(Long productId, long delta) {
        productMetricsRepository.incrementLikeCount(productId, delta);
    }

    public void incrementViewCount(Long productId, long delta) {
        productMetricsRepository.incrementViewCount(productId, delta);
    }

    public void incrementSales(Long productId, long countDelta, BigDecimal amountDelta) {
        productMetricsRepository.incrementSales(productId, countDelta, amountDelta);
    }
}
