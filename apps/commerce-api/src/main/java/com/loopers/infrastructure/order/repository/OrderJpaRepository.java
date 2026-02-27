package com.loopers.infrastructure.order.repository;

import com.loopers.infrastructure.order.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.List;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findAllByMemberIdAndCreatedAtBetween(Long memberId, ZonedDateTime startAt, ZonedDateTime endAt);
}
