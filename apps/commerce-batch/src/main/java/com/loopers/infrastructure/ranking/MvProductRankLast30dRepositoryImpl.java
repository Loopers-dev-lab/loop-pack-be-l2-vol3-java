package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.mv.MvProductRankLast30d;
import com.loopers.domain.ranking.mv.MvProductRankLast30dRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class MvProductRankLast30dRepositoryImpl implements MvProductRankLast30dRepository {

    private final MvProductRankLast30dJpaRepository jpaRepository;

    @Override
    public MvProductRankLast30d save(MvProductRankLast30d entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public int deleteByAnchorDate(LocalDate anchorDate) {
        return jpaRepository.deleteByAnchorDate(anchorDate);
    }

    @Override
    public long countByAnchorDate(LocalDate anchorDate) {
        return jpaRepository.countByAnchorDate(anchorDate);
    }
}
