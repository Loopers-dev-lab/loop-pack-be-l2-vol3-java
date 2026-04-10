package com.loopers.application.user;

import com.loopers.domain.notification.NotificationSender;
import com.loopers.domain.user.event.UserSignedUpEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignupNotificationHandler {

    private final NotificationSender notificationSender;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(UserSignedUpEvent event) {
        try {
            notificationSender.send(
                    event.email(),
                    "회원가입을 축하합니다!",
                    event.name() + "님, 회원가입을 축하합니다!");
        } catch (Exception e) {
            log.warn("[SignupNotification] 알림 발송 실패 — userId={}", event.userId(), e);
        }
    }
}
