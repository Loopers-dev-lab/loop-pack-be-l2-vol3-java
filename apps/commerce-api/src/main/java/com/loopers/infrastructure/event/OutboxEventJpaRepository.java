package com.loopers.infrastructure.event;

import com.loopers.domain.event.OutboxEventModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventModel, Long> {
    List<OutboxEventModel> findTop100ByPublishedAtIsNullOrderByIdAsc();

    Optional<OutboxEventModel> findByEventId(String eventId);
}
