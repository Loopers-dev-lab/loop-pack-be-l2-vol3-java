package com.loopers.application.event;

import com.loopers.domain.event.OrderExpiredEvent;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.order.model.Orders;
import com.loopers.domain.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class OrderExpireScheduler {

    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void expireUnpaidOrders() {
        List<Orders> expiredOrders = orderService.findExpiredOrders();
        if (expiredOrders.isEmpty()) {
            return;
        }

        for (Orders order : expiredOrders) {
            orderService.updateOrderStatus(order.getId(), OrderStatus.CANCELLED);
            eventPublisher.publishEvent(new OrderExpiredEvent(order.getId(), order.getUserCouponId()));
            log.info("주문 만료 처리 - orderId: {}", order.getId());
        }
    }
}
