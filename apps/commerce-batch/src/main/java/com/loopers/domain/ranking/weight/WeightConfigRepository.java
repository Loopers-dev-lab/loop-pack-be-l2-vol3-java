package com.loopers.domain.ranking.weight;

import java.util.List;

public interface WeightConfigRepository {

    // Command (test fixture 용)
    WeightConfig save(WeightConfig entity);

    // Query
    List<WeightConfig> findAllByActiveTrue();
}
