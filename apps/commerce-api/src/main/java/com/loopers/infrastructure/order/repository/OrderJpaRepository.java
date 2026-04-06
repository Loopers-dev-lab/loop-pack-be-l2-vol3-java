package com.loopers.infrastructure.order.repository;

import com.loopers.domain.order.OrderStatus;
import com.loopers.infrastructure.order.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findAllByMemberIdAndCreatedAtBetween(Long memberId, ZonedDateTime startAt, ZonedDateTime endAt);

    Optional<OrderEntity> findByOrderNumber(String orderNumber);

    List<OrderEntity> findByStatusAndCreatedAtBefore(OrderStatus status, ZonedDateTime createdAt);
}
