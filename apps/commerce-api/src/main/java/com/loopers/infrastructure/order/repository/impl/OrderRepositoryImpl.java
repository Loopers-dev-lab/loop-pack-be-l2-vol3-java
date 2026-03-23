package com.loopers.infrastructure.order.repository.impl;

import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.order.model.Orders;
import com.loopers.domain.order.repository.OrderRepository;
import com.loopers.infrastructure.order.entity.OrderEntity;
import com.loopers.infrastructure.order.repository.OrderJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Orders save(Orders orders) {
        OrderEntity entity = orderJpaRepository.save(OrderEntity.toEntity(orders));
        return entity.toModel();
    }

    @Override
    public Optional<Orders> findById(Long id) {
        return orderJpaRepository.findById(id).map(OrderEntity::toModel);
    }

    @Override
    public Optional<Orders> findByOrderNumber(String orderNumber) {
        return orderJpaRepository.findByOrderNumber(orderNumber).map(OrderEntity::toModel);
    }

    @Override
    public void updateStatus(Long orderId, OrderStatus status) {
        OrderEntity entity = orderJpaRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다."));
        entity.updateStatus(status);
    }

    @Override
    public List<Orders> findByMemberIdAndCreatedAtBetween(Long memberId, LocalDateTime startAt, LocalDateTime endAt) {
        ZonedDateTime zonedStart = startAt.atZone(ZoneId.systemDefault());
        ZonedDateTime zonedEnd = endAt.atZone(ZoneId.systemDefault());
        return orderJpaRepository.findAllByMemberIdAndCreatedAtBetween(memberId, zonedStart, zonedEnd).stream()
                .map(OrderEntity::toModel)
                .toList();
    }
}
