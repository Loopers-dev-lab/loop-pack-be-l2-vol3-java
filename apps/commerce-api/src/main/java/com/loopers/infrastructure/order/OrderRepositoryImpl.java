package com.loopers.infrastructure.order;

import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.vo.OrderId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public OrderModel save(OrderModel order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Optional<OrderModel> findById(Long id) {
        return orderJpaRepository.findById(id);
    }

    @Override
    public Optional<OrderModel> findByOrderId(OrderId orderId) {
        return orderJpaRepository.findByOrderId(orderId);
    }

    @Override
    public Page<OrderModel> findByRefMemberId(RefMemberId refMemberId, LocalDateTime startDateTime, LocalDateTime endDateTime, Pageable pageable) {
        return orderJpaRepository.findByRefMemberIdWithDateFilter(refMemberId, startDateTime, endDateTime, pageable);
    }
}
