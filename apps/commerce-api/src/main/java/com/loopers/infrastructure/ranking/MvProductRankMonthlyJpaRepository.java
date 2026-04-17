package com.loopers.infrastructure.ranking;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthlyEntity, Long> {

    List<MvProductRankMonthlyEntity> findAllByYearMonthOrderByProductRankAsc(String yearMonth, Pageable pageable);
}
