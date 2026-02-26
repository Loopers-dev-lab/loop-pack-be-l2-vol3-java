package com.loopers.infrastructure.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {

    List<OrderJpaEntity> findAllByUserId(String userId);

    List<OrderJpaEntity> findAllByUserIdAndCreatedAtBetween(String userId, LocalDateTime startAt, LocalDateTime endAt);
}
