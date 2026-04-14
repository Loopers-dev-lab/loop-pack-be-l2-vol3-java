package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankWeeklyModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeeklyModel, Long> {

    /** 특정 주차의 기존 랭킹 데이터를 일괄 삭제 (배치 재실행 대비) */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM MvProductRankWeeklyModel m WHERE m.yearWeek = :yearWeek")
    void deleteByYearWeek(@Param("yearWeek") String yearWeek);
}
