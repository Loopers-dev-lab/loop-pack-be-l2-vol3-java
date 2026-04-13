package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.domain.ranking.MvProductRankWeeklyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MvProductRankWeeklyRepositoryImpl implements MvProductRankWeeklyRepository {

    private final MvProductRankWeeklyJpaRepository jpaRepository;

    @Override
    public List<MvProductRankWeekly> findByDate(LocalDate date, int page, int size) {
        return jpaRepository.findByDate(date, PageRequest.of(page, size));
    }
}
