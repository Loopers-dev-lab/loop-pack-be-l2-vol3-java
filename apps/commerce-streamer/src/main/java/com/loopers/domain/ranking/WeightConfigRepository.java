package com.loopers.domain.ranking;

import java.util.List;

public interface WeightConfigRepository {

    List<WeightConfig> findAllByActiveTrue();
}
