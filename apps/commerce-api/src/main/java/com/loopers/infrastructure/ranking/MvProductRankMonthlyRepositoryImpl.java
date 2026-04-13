package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankMonthly;
import com.loopers.domain.ranking.MvProductRankMonthlyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MvProductRankMonthlyRepositoryImpl implements MvProductRankMonthlyRepository {

    private final MvProductRankMonthlyJpaRepository jpaRepository;

    @Override
    public List<MvProductRankMonthly> findByDate(LocalDate date, int page, int size) {
        return jpaRepository.findByDate(date, PageRequest.of(page, size));
    }
}
