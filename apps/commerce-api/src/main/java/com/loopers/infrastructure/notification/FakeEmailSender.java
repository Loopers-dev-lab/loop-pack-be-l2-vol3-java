package com.loopers.infrastructure.notification;

import com.loopers.domain.notification.NotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FakeEmailSender implements NotificationSender {

    @Override
    public void send(String to, String subject, String body) {
        log.info("[FakeEmail] to={}, subject={}, body={}", to, subject, body);
    }
}
