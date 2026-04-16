package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankMonthly;
import com.loopers.domain.ranking.MvProductRankMonthlyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class MvProductRankMonthlyRepositoryImpl implements MvProductRankMonthlyRepository {

    private final MvProductRankMonthlyJpaRepository jpaRepository;

    @Override
    public List<MvProductRankMonthly> findTop(int page, int size) {
        return jpaRepository.findAllByOrderByScoreDesc(PageRequest.of(page, size)).getContent();
    }
}
