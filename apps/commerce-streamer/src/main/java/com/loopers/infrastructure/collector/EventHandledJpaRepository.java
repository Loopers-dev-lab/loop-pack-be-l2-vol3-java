package com.loopers.infrastructure.collector;

import com.loopers.domain.collector.EventHandledModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledJpaRepository extends JpaRepository<EventHandledModel, Long> {
    boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup);
}
