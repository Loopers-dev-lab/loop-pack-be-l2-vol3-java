package com.loopers.domain.metrics;

import java.time.LocalDate;
import java.util.List;

public interface ProductMetricsRepository {

    List<ProductScoreProjection> findTopScores(LocalDate start, LocalDate end, int limit);
}
