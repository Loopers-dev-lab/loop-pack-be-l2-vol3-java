package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeatureFlagJpaRepository extends JpaRepository<FeatureFlag, Long> {

    Optional<FeatureFlag> findByFeatureKey(String featureKey);
}
