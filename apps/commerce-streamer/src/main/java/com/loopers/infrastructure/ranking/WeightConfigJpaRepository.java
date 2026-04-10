package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.WeightConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeightConfigJpaRepository extends JpaRepository<WeightConfig, Long> {

    List<WeightConfig> findAllByActiveTrue();
}
