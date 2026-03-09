package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 주문 Repository 구현체.
 * 고객용: userId+기간 조회. 어드민용: 기간만으로 전체 주문 조회({@link #findAllByOrderedAtBetween}).
 */
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

    /** 어드민용: 사용자 구분 없이 기간·페이징으로 주문 목록 조회. [start, end) 구간, orderedAt 내림차순. */
    @Override
    public List<OrderModel> findAllByOrderedAtBetween(ZonedDateTime start, ZonedDateTime end, int page, int size) {
        return orderJpaRepository
                .findByOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtDesc(
                        start, end, PageRequest.of(page, size))
                .getContent();
    }
}
