package com.loopers.domain.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductMetricsService {

    private final ProductMetricsRepository productMetricsRepository;

    @Transactional
    public void applyLikeDelta(Long productDbId, int delta, LocalDateTime eventAt) {
        productMetricsRepository.findByRefProductId(productDbId)
                .ifPresentOrElse(
                        metrics -> {
                            if (!metrics.isStale(eventAt)) {
                                metrics.applyDelta(delta, eventAt);
                            }
                        },
                        () -> productMetricsRepository.save(ProductMetricsModel.create(productDbId, delta, eventAt))
                );
    }
}
