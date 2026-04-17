package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.weight.WeightConfig;
import com.loopers.domain.ranking.weight.WeightConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class WeightConfigRepositoryImpl implements WeightConfigRepository {

    private final WeightConfigJpaRepository jpaRepository;

    @Override
    public WeightConfig save(WeightConfig entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public List<WeightConfig> findAllByActiveTrue() {
        return jpaRepository.findAllByActiveTrue();
    }
}
