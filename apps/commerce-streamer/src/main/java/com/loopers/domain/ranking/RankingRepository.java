package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.Map;

public interface RankingRepository {

    void flush(Map<LocalDate, Map<Long, Double>> deltaByDateAndProduct);
}
