package com.loopers.application.queue;

import com.loopers.domain.queue.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class QueueFacade {

    private final QueueService queueService;

    public QueueInfo enter(String userId) {
        long position = queueService.enter(userId);
        long totalCount = queueService.getTotalCount();
        return new QueueInfo(position, totalCount);
    }

    public QueueInfo getPosition(String userId) {
        long position = queueService.getPosition(userId);
        long totalCount = queueService.getTotalCount();
        return new QueueInfo(position, totalCount);
    }
}