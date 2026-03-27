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
        productMetricsRepository.upsertLikeDelta(productDbId, delta, eventAt);
    }
}
