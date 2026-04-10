package com.loopers.infrastructure.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, Long> {

    List<OutboxEventEntity> findTop100ByPublishedFalseOrderByCreatedAtAsc();
}
