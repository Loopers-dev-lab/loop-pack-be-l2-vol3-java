package com.loopers.domain.queue;

import java.util.Optional;

// 피처 플래그 저장소 인터페이스. Domain 레이어에 정의하여 DIP를 적용
// 구현체(FeatureFlagRepositoryImpl)에서 JPA를 통해 DB에 접근
public interface FeatureFlagRepository {

    // featureKey로 피처 플래그를 조회한다. 존재하지 않으면 Optional.empty() 반환.
    Optional<FeatureFlag> findByFeatureKey(String featureKey);

    FeatureFlag save(FeatureFlag featureFlag);
}
