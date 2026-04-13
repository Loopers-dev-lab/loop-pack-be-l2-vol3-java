package com.loopers.domain.metrics;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductMetricsRepository {

    List<ProductScoreProjection> findTopScores(LocalDateTime start, LocalDateTime end, int limit);
}
