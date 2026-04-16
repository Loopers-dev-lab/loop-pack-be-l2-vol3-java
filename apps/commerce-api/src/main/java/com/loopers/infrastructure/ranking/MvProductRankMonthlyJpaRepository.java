package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankMonthlyReadModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthlyReadModel, Long> {

    /** 특정 월의 랭킹을 순위순으로 페이징 조회 */
    List<MvProductRankMonthlyReadModel> findByYearMonthOrderByRankingAsc(String yearMonth, Pageable pageable);
}
