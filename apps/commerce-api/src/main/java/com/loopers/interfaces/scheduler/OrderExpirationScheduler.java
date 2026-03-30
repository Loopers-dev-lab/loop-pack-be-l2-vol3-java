package com.loopers.interfaces.scheduler;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderService;
import com.loopers.domain.order.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final OrderService orderService;
    private final OrderFacade orderFacade;

    @Scheduled(fixedDelayString = "${order.expiration.interval-ms:60000}")
    public void expireCreatedOrders() {
        List<Order> expired = orderService.findCreatedOlderThanWithItems(
                ZonedDateTime.now().minusMinutes(10));
        if (expired.isEmpty()) return;

        log.info("주문 만료 대상 {}건 탐지", expired.size());

        for (Order order : expired) {
            try {
                orderFacade.expireOrder(order.getId());
                log.info("주문 만료 처리 완료: orderId={}", order.getId());
            } catch (Exception e) {
                log.warn("주문 만료 처리 실패: orderId={}", order.getId(), e);
            }
        }
    }
}
