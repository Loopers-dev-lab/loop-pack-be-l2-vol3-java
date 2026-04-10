package com.loopers.domain.notification;

public interface NotificationSender {

    void send(String to, String subject, String body);
}
