package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingDeltaPending;
import com.loopers.domain.ranking.RankingDeltaPendingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface RankingDeltaPendingJpaRepository extends JpaRepository<RankingDeltaPending, Long> {

    List<RankingDeltaPending> findAllByEventIdInAndStatus(List<String> eventIds, RankingDeltaPendingStatus status);

    @Transactional
    @Modifying
    @Query("UPDATE RankingDeltaPending r SET r.status = com.loopers.domain.ranking.RankingDeltaPendingStatus.FLUSHED WHERE r.id IN :ids")
    void markAsFlushed(@Param("ids") List<Long> ids);
}
