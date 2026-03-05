package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(Long id);
    Optional<Order> findByIdWithLock(Long id);
    List<Order> findByUserId(Long userId);
    List<Order> findAll();
    Page<Order> findAll(Pageable pageable);
}
