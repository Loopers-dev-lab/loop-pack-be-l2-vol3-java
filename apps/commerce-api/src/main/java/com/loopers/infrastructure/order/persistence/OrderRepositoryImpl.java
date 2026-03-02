package com.loopers.infrastructure.order.persistence;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * {@link OrderRepository}의 인프라스트럭처 구현체.
 *
 * <p>{@link OrderJpaRepository}에 위임하여 주문 영속성을 처리한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Order save(Order order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Optional<Order> findByIdWithItems(Long orderId) {
        return orderJpaRepository.findByIdWithItems(orderId);
    }

    @Override
    public Slice<Order> findAll(Pageable pageable) {
        return orderJpaRepository.findAllBy(pageable);
    }

    @Override
    public Slice<Order> findAllByUserIdAndOrderedAtBetween(Long userId, LocalDateTime start, LocalDateTime end,
            Pageable pageable) {
        return orderJpaRepository.findAllByUserIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThan(
                userId, start, end, pageable);
    }
}
