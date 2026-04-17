package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.mv.MvProductRankLast7d;
import com.loopers.domain.ranking.mv.MvProductRankLast7dRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class MvProductRankLast7dRepositoryImpl implements MvProductRankLast7dRepository {

    private final MvProductRankLast7dJpaRepository jpaRepository;

    @Override
    public MvProductRankLast7d save(MvProductRankLast7d entity) {
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
