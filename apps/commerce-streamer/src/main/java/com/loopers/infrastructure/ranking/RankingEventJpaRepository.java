package com.loopers.infrastructure.ranking;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RankingEventJpaRepository extends JpaRepository<RankingEventEntity, Long> {
}
