package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    public OrderRepositoryImpl(OrderJpaRepository orderJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
    }

    @Override
    public Optional<OrderModel> findById(Long orderId) {
        return orderJpaRepository.findByIdWithOrderItems(orderId);
    }

    @Override
    public OrderModel save(OrderModel order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public List<OrderModel> findByUserIdAndOrderedAtBetween(
            Long userId,
            ZonedDateTime start,
            ZonedDateTime end,
            int page,
            int size
    ) {
        return orderJpaRepository
                .findByUserIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtDesc(
                        userId, start, end, PageRequest.of(page, size))
                .getContent();
    }
}
