package com.loopers.domain.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductMetricsService {

    private final ProductMetricsRepository productMetricsRepository;

    @Transactional
    public void adjustLikeCount(Long refProductId, int delta) {
        productMetricsRepository.findByRefProductId(refProductId)
                .ifPresentOrElse(
                        metrics -> metrics.adjustLikeCount(delta),
                        () -> productMetricsRepository.save(ProductMetricsModel.create(refProductId, delta))
                );
    }
}
