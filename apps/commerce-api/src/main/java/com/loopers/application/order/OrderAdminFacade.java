package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class OrderAdminFacade {

    private final OrderService orderService;

    /**
     * 전체 주문 목록 조회 (US-O04, BR-O07)
     */
    @Transactional(readOnly = true)
    public Page<OrderInfo> findAll(Pageable pageable) {
        return orderService.findAll(pageable).map(OrderInfo::of);
    }

    /**
     * 단일 주문 상세 조회 (US-O05)
     * 관리자는 소유권 확인 없이 모든 주문 조회 가능
     */
    @Transactional(readOnly = true)
    public OrderInfo findById(Long orderId) {
        Order order = orderService.findById(orderId);
        return OrderInfo.of(order);
    }
}
