package com.loopers.infrastructure.score;

import com.loopers.domain.score.MvProductScoreDailyId;
import com.loopers.domain.score.MvProductScoreDailyModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MvProductScoreDailyJpaRepository extends JpaRepository<MvProductScoreDailyModel, MvProductScoreDailyId> {
}
