package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.weight.WeightConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface WeightConfigJpaRepository extends JpaRepository<WeightConfig, Long> {

    List<WeightConfig> findAllByActiveTrue();
}
