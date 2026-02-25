package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
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
    public Page<OrderModel> findAll(Pageable pageable) {
        return orderJpaRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Override
    public List<OrderModel> findAllByUserIdAndPeriod(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderJpaRepository.findAllByUserIdAndPeriod(userId, startAt, endAt);
    }

    @Override
    public Optional<OrderModel> findDetailById(Long id) {
        return orderJpaRepository.findDetailById(id);
    }

    @Override
    public Optional<OrderModel> findDetailByIdAndUserId(Long id, Long userId) {
        return orderJpaRepository.findDetailByIdAndUserId(id, userId);
    }
}
