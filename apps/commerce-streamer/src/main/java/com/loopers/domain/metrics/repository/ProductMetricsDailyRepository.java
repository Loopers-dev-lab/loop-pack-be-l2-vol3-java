package com.loopers.domain.metrics.repository;

import java.time.LocalDate;

public interface ProductMetricsDailyRepository {

    void incrementViewCount(LocalDate metricDate, Long productId, double scoreDelta);

    void incrementLikeCount(LocalDate metricDate, Long productId, double scoreDelta);

    void decrementLikeCount(LocalDate metricDate, Long productId, double scoreDelta);

    void incrementOrderCount(LocalDate metricDate, Long productId, double scoreDelta);
}
