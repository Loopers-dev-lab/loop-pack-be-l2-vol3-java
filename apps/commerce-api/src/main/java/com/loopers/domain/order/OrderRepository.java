package com.loopers.domain.order;

import com.loopers.domain.common.CursorResult;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(Long id);
    List<Order> findAllByUserId(Long userId, ZonedDateTime startAt, ZonedDateTime endAt);
    List<Order> findAllByUserIdWithItems(Long userId, ZonedDateTime startAt, ZonedDateTime endAt);

    /** 주문 목록 커서 조회 (created_at DESC, id DESC) */
    CursorResult<Order> findAllByUserIdWithCursor(Long userId, ZonedDateTime startAt, ZonedDateTime endAt,
                                                   ZonedDateTime cursorCreatedAt, Long cursorId, int size);

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findAll(int page, int size);
    long count();
}
