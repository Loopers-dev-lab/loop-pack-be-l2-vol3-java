package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Order save(Order order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return orderJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<Order> findAllByMemberId(Long memberId) {
        return orderJpaRepository.findAllByMemberIdAndDeletedAtIsNull(memberId);
    }

    @Override
    public List<Order> findAllByMemberIdAndCreatedAtBetween(Long memberId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderJpaRepository.findAllByMemberIdAndCreatedAtBetweenAndDeletedAtIsNull(memberId, startAt, endAt);
    }

    @Override
    public List<Order> findAll() {
        return orderJpaRepository.findAllByDeletedAtIsNull();
    }
}
