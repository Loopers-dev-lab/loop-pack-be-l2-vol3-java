package com.loopers.infrastructure.ranking;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeeklyEntity, Long> {

    List<MvProductRankWeeklyEntity> findAllByYearWeekOrderByScoreDesc(String yearWeek);

    void deleteAllByYearWeekNot(String yearWeek);
}
