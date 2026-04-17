package com.loopers.domain.rank;

import java.math.BigDecimal;

public interface BatchRankingWeightRepository {

    BigDecimal findWeightByEventType(String eventType);
}
