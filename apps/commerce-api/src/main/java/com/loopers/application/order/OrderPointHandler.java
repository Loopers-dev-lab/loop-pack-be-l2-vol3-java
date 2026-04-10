package com.loopers.application.order;

import com.loopers.domain.order.event.OrderCompletedEvent;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPointHandler {

    private final UserService userService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCompletedEvent event) {
        try {
            long pointAmount = (long) (event.totalPrice() * 0.02);
            if (pointAmount > 0) {
                userService.addPoint(event.userId(), pointAmount);
            }
        } catch (Exception e) {
            log.warn("[OrderPoint] 포인트 적립 실패 — orderId={}, userId={}",
                    event.orderId(), event.userId(), e);
        }
    }
}
