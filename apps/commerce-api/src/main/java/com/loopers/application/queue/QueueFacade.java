package com.loopers.application.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class QueueFacade {

    private final QueueService queueService;
    private final QueueTokenService queueTokenService;

    public QueueInfo enter(String eventId, Long userId) {
        QueueService.QueueStatus status = queueService.enter(eventId, userId);
        return QueueInfo.from(status);
    }

    public QueueInfo getPosition(String eventId, Long userId) {
        QueueService.QueueStatus status = queueService.getPosition(eventId, userId);
        return queueTokenService.getTokenInfo(eventId, userId)
                .map(tokenInfo -> QueueInfo.withToken(status, tokenInfo.token(), tokenInfo.expiresIn()))
                .orElse(QueueInfo.from(status));
    }
}
