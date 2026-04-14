package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankWeeklyReadModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeeklyReadModel, Long> {

    /** 특정 주차의 랭킹을 순위순으로 페이징 조회 */
    List<MvProductRankWeeklyReadModel> findByYearWeekOrderByRankingAsc(String yearWeek, Pageable pageable);
}
