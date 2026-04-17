package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.WeightConfig;
import com.loopers.domain.ranking.WeightConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class WeightConfigRepositoryImpl implements WeightConfigRepository {

    private final WeightConfigJpaRepository jpaRepository;

    @Override
    public List<WeightConfig> findAllByActiveTrue() {
        return jpaRepository.findAllByActiveTrue();
    }
}
