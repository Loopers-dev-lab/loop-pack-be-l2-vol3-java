package com.loopers.domain.ranking;

import java.util.List;
import java.util.Optional;

public interface WeightConfigRepository {

    List<WeightConfig> findAllByActiveTrue();

    Optional<WeightConfig> findByGroupName(String groupName);

    WeightConfig save(WeightConfig config);
}
