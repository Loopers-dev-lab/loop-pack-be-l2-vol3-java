package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Order save(Order order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return orderJpaRepository.findById(id)
                .filter(order -> order.getDeletedAt() == null);
    }

    @Override
    public List<Order> findAllByUserIdAndCreatedAtBetween(Long userId, ZonedDateTime from, ZonedDateTime to) {
        return orderJpaRepository.findAllByUserIdAndCreatedAtBetweenAndDeletedAtIsNull(userId, from, to);
    }

    @Override
    public Page<Order> findAll(Pageable pageable) {
        return orderJpaRepository.findAllByDeletedAtIsNull(pageable);
    }
}
