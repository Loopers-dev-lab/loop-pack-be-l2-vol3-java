package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.WeightConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WeightConfigJpaRepository extends JpaRepository<WeightConfig, Long> {

    List<WeightConfig> findAllByActiveTrue();

    Optional<WeightConfig> findByGroupName(String groupName);
}
