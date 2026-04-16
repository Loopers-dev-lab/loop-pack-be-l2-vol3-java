package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MonthlyRankingRepository;
import com.loopers.domain.ranking.RankingEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * MonthlyRankingRepository JPA 구현체.
 *
 * WeeklyRankingRepositoryImpl 과 구조가 동일하며, 대상 JPA 레포지토리만 다르다.
 * MvProductRankMonthlyJpaRepository 를 감싸서 JPA 엔티티를 RankingEntry 로 변환한다.
 */
@RequiredArgsConstructor
@Component
public class MonthlyRankingRepositoryImpl implements MonthlyRankingRepository {

    private final MvProductRankMonthlyJpaRepository jpaRepository;

    /**
     * pageOneBased 를 JPA 의 0-based 페이지로 변환하여 조회한다.
     * baseDate 가 null 이거나 size 가 0 이하이면 빈 리스트를 반환한다.
     */
    @Override
    public List<RankingEntry> getTopN(LocalDate baseDate, int pageOneBased, int size) {
        if (baseDate == null || size <= 0) return Collections.emptyList();
        // API 는 1-based 페이지를 사용하므로 JPA PageRequest 에는 (page-1)을 전달
        int page = Math.max(pageOneBased, 1);
        List<MvProductRankMonthly> entities = jpaRepository.findByBaseDateOrderByRankAsc(
                baseDate, PageRequest.of(page - 1, size));
        return entities.stream()
                .map(e -> new RankingEntry(e.getProductId(), e.getRank(), e.getScore()))
                .toList();
    }

    @Override
    public long getTotal(LocalDate baseDate) {
        if (baseDate == null) return 0L;
        return jpaRepository.countByBaseDate(baseDate);
    }
}
