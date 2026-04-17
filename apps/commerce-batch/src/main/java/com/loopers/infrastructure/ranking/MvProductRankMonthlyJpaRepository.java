package com.loopers.infrastructure.ranking;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthlyEntity, Long> {

    List<MvProductRankMonthlyEntity> findAllByYearMonthOrderByScoreDesc(String yearMonth);

    void deleteAllByYearMonthNot(String yearMonth);
}
