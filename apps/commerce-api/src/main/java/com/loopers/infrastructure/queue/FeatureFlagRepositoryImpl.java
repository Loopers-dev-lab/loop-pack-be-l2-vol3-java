package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.FeatureFlag;
import com.loopers.domain.queue.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// FeatureFlagRepository의 JPA 구현체.
// Domain 레이어의 인터페이스를 Infrastructure 레이어에서 구현하여 DIP를 적용한다.
@RequiredArgsConstructor
@Repository
public class FeatureFlagRepositoryImpl implements FeatureFlagRepository {

    private final FeatureFlagJpaRepository featureFlagJpaRepository;

    @Override
    public Optional<FeatureFlag> findByFeatureKey(String featureKey) {
        return featureFlagJpaRepository.findByFeatureKey(featureKey);
    }

    @Override
    public FeatureFlag save(FeatureFlag featureFlag) {
        return featureFlagJpaRepository.save(featureFlag);
    }
}
