package com.loopers.infrastructure.outbox.repository;

import com.loopers.domain.outbox.model.OutboxStatus;
import com.loopers.infrastructure.outbox.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, Long> {

    List<OutboxEventEntity> findByStatusOrderByIdAsc(OutboxStatus status);
}
