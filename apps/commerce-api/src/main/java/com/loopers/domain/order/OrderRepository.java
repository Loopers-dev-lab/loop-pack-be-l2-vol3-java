package com.loopers.domain.order;

import java.util.List;
import java.util.Optional;

/**
 * 주문 Repository 인터페이스.
 * 순수 Java 인터페이스로 Spring/JPA 의존성 없음.
 * 구현체는 Infrastructure Layer에 위치.
 */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    List<Order> findAllByUserId(Long userId, int offset, int limit);

    long countByUserId(Long userId);
}
