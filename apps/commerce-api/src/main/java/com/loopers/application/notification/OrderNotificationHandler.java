package com.loopers.application.notification;

import com.loopers.domain.order.event.OrderCompletedEvent;
import com.loopers.domain.notification.NotificationSender;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationHandler {

    private final UserService userService;
    private final NotificationSender notificationSender;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCompletedEvent event) {
        try {
            String email = userService.getById(event.userId()).getEmail();
            notificationSender.send(
                    email,
                    "주문이 확정되었습니다",
                    String.format("주문번호 %d의 결제가 완료되어 주문이 확정되었습니다.", event.orderId()));
        } catch (Exception e) {
            log.warn("[Email] 발송 실패 — orderId={}, userId={}",
                    event.orderId(), event.userId(), e);
        }
    }
}
