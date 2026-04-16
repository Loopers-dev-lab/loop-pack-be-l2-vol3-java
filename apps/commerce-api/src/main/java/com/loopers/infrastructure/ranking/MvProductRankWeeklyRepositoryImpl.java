package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.domain.ranking.MvProductRankWeeklyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class MvProductRankWeeklyRepositoryImpl implements MvProductRankWeeklyRepository {

    private final MvProductRankWeeklyJpaRepository jpaRepository;

    @Override
    public List<MvProductRankWeekly> findTop(int page, int size) {
        return jpaRepository.findAllByOrderByScoreDesc(PageRequest.of(page, size)).getContent();
    }
}
