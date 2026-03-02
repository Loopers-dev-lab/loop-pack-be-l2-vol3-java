package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

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
    public Optional<Order> findActiveById(Long id) {
        return orderJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Page<Order> findAllActiveByUserId(Long userId, Pageable pageable) {
        return orderJpaRepository.findAllByUserIdAndDeletedAtIsNull(userId, pageable);
    }

    @Override
    public Page<Order> findAllActive(Pageable pageable) {
        return orderJpaRepository.findAllByDeletedAtIsNull(pageable);
    }
}
