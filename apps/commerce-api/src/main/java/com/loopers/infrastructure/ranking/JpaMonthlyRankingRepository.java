package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MonthlyRankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Repository
public class JpaMonthlyRankingRepository implements MonthlyRankingRepository {

    private final MonthlyRankingJpaRepository jpaRepository;

    @Override
    public List<Long> findProductIdsByBaseDate(LocalDate baseDate, long offset, long limit) {
        int pageNumber = (int) (offset / limit);
        PageRequest pageRequest = PageRequest.of(pageNumber, (int) limit);
        return jpaRepository.findProductIdsByBaseDate(baseDate, pageRequest);
    }

    @Override
    public long countByBaseDate(LocalDate baseDate) {
        return jpaRepository.countByBaseDate(baseDate);
    }
}
