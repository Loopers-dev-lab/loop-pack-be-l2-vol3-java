package com.loopers.infrastructure.metrics.persistence;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.domain.metrics.ProductScoreProjection;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public List<ProductScoreProjection> findTopScores(LocalDateTime start, LocalDateTime end, int limit) {
        return productMetricsJpaRepository.findTopScores(start, end, PageRequest.of(0, limit));
    }
}
