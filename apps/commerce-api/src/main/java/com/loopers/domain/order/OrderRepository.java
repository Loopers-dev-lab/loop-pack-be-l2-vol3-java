package com.loopers.domain.order;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 주문 영속성 인터페이스.
 * 구현체는 infrastructure 레이어에 둔다.
 */
public interface OrderRepository {

    Optional<OrderModel> findById(Long orderId);

    /** 비관적 락. 동시 PENDING 중복 방지 등에 사용. */
    Optional<OrderModel> findByIdForUpdate(Long orderId);

    OrderModel save(OrderModel order);

    List<OrderModel> findByUserIdAndOrderedAtBetween(
            Long userId,
            ZonedDateTime start,
            ZonedDateTime end,
            int page,
            int size);

    /** 어드민용: 사용자 구분 없이 기간·페이징 조회. */
    List<OrderModel> findAllByOrderedAtBetween(ZonedDateTime start, ZonedDateTime end, int page, int size);
}
